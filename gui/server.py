#!/usr/bin/env python3
"""
Simple HTTP server for QA Agent GUI
Serves the web interface and provides API endpoints with SQLite persistence
"""

import http.server
import socketserver
import json
import os
import subprocess
import sqlite3
import shutil
from urllib.parse import urlparse, parse_qs
from pathlib import Path

PORT = 8080
ROOT_DIR = Path(__file__).resolve().parents[1]
GUI_DIR = ROOT_DIR / "gui"
DATA_DIR = ROOT_DIR / "data"
DB_PATH = DATA_DIR / "qa_agent.db"
REPORT_PATH = ROOT_DIR / "output" / "reports" / "relatorio.html"

def resolve_maven_cmd():
    cmd = os.environ.get("MAVEN_CMD")
    if cmd and cmd.strip():
        return cmd.strip()
    found = shutil.which("mvn")
    if found:
        return found
    found = shutil.which("mvn.cmd")
    if found:
        return found
    return "mvn"

def init_db():
    """Initialize SQLite database with required tables"""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH))
    cursor = conn.cursor()
    
    # Tests table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS tests (
            id TEXT PRIMARY KEY,
            module TEXT NOT NULL,
            menu TEXT NOT NULL,
            test_type TEXT,
            priority TEXT,
            name TEXT NOT NULL,
            description TEXT,
            test_data TEXT,
            feature TEXT,
            java TEXT,
            status TEXT,
            created_at TEXT,
            updated_at TEXT
        )
    ''')
    
    # Menu structure table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS menu_structure (
            key TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            structure_json TEXT NOT NULL
        )
    ''')
    
    # Config table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS config (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    ''')
    
    conn.commit()
    conn.close()

class GuiHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(GUI_DIR), **kwargs)
    
    def do_GET(self):
        parsed_path = urlparse(self.path)

        if parsed_path.path == '/api/load-tests':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('SELECT * FROM tests')
                rows = cursor.fetchall()
                columns = [desc[0] for desc in cursor.description]

                tests = []
                for row in rows:
                    test = dict(zip(columns, row))
                    test['testData'] = test.get('test_data') or ''
                    test['testType'] = test.get('test_type')
                    test['createdAt'] = test.get('created_at')
                    test['updatedAt'] = test.get('updated_at')
                    test.pop('test_data', None)
                    test.pop('test_type', None)
                    test.pop('created_at', None)
                    test.pop('updated_at', None)
                    tests.append(test)

                conn.close()

                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(tests).encode())
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
            return

        if parsed_path.path == '/api/load-menu-structure':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('SELECT structure_json FROM menu_structure WHERE key = ?', ('main',))
                row = cursor.fetchone()
                conn.close()

                structure = json.loads(row[0]) if row else {}

                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(structure).encode())
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
            return

        if parsed_path.path == '/api/load-config':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('SELECT key, value FROM config')
                rows = cursor.fetchall()
                conn.close()

                config = {}
                for key, value in rows:
                    try:
                        config[key] = json.loads(value)
                    except Exception:
                        config[key] = value

                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(config).encode())
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
            return
        
        # Serve reports
        if parsed_path.path == '/reports':
            if REPORT_PATH.exists():
                self.send_response(200)
                self.send_header('Content-type', 'text/html')
                self.end_headers()
                self.wfile.write(REPORT_PATH.read_text(encoding='utf-8', errors='replace').encode('utf-8'))
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Relatorio nao encontrado. Execute os testes primeiro.")
            return
        
        # Serve static files
        super().do_GET()
    
    def do_POST(self):
        parsed_path = urlparse(self.path)
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        
        # API: Generate test
        if parsed_path.path == '/api/generate-test':
            try:
                data = json.loads(post_data.decode('utf-8'))
                module = data.get('module', '')
                menu = data.get('menu', '')
                test_type = data.get('testType', 'smoke')
                priority = data.get('priority', 'Alta')
                test_name = data.get('testName', '')
                description = data.get('description', '')
                
                # Generate mock artifacts
                feature = self.generate_mock_feature(module, menu, test_name, description)
                java_code = self.generate_mock_java(module, menu, test_name)
                
                response = {
                    'feature': feature,
                    'java': java_code,
                    'jira': description
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Apply test
        elif parsed_path.path == '/api/apply-test':
            try:
                data = json.loads(post_data.decode('utf-8'))
                module = data.get('module', '')
                menu = data.get('menu', '')
                test_type = data.get('testType', 'smoke')
                test_name = data.get('testName', '')
                feature_content = data.get('feature', '')
                java_content = data.get('java', '')
                
                # Convert to folder names
                menu_folder = menu.lower().replace(' ', '_')
                module_folder = module.lower().replace(' ', '_')
                
                # Create feature file
                feature_path = Path(f"src/test/resources/features/{test_type}/{module_folder}/{menu_folder}.feature")
                feature_path.parent.mkdir(parents=True, exist_ok=True)
                feature_path.write_text(feature_content)
                
                # Create Java test file
                class_name = ''.join(c for c in test_name if c.isalnum()) + 'Test'
                java_path = Path(f"src/test/java/br/com/qasuite/pages/{module_folder}/{class_name}.java")
                java_path.parent.mkdir(parents=True, exist_ok=True)
                java_path.write_text(java_content)
                
                response = {
                    'status': 'success',
                    'featurePath': str(feature_path),
                    'javaPath': str(java_path)
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Save test to database
        elif parsed_path.path == '/api/save-test':
            try:
                data = json.loads(post_data.decode('utf-8'))
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                
                cursor.execute('''
                    INSERT OR REPLACE INTO tests 
                    (id, module, menu, test_type, priority, name, description, test_data, feature, java, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''', (
                    data.get('id'),
                    data.get('module'),
                    data.get('menu'),
                    data.get('testType'),
                    data.get('priority'),
                    data.get('name'),
                    data.get('description'),
                    json.dumps(data.get('testData', {})),
                    data.get('feature'),
                    data.get('java'),
                    data.get('status', 'draft'),
                    data.get('createdAt'),
                    data.get('updatedAt')
                ))
                
                conn.commit()
                conn.close()
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success'}).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Load all tests from database
        elif parsed_path.path == '/api/load-tests':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                
                cursor.execute('SELECT * FROM tests')
                rows = cursor.fetchall()
                columns = [desc[0] for desc in cursor.description]
                
                tests = []
                for row in rows:
                    test = dict(zip(columns, row))
                    test['testData'] = test.get('test_data') or ''
                    test['testType'] = test.get('test_type')
                    test['createdAt'] = test.get('created_at')
                    test['updatedAt'] = test.get('updated_at')
                    test.pop('test_data', None)
                    test.pop('test_type', None)
                    test.pop('created_at', None)
                    test.pop('updated_at', None)
                    tests.append(test)
                
                conn.close()
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(tests).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Delete test from database
        elif parsed_path.path == '/api/delete-test':
            try:
                data = json.loads(post_data.decode('utf-8'))
                test_id = data.get('id')
                
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('DELETE FROM tests WHERE id = ?', (test_id,))
                conn.commit()
                conn.close()
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success'}).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Save menu structure to database
        elif parsed_path.path == '/api/save-menu-structure':
            try:
                data = json.loads(post_data.decode('utf-8'))
                structure_json = json.dumps(data)
                
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('INSERT OR REPLACE INTO menu_structure (key, name, structure_json) VALUES (?, ?, ?)',
                              ('main', 'Menu Structure', structure_json))
                conn.commit()
                conn.close()
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success'}).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Load menu structure from database
        elif parsed_path.path == '/api/load-menu-structure':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('SELECT structure_json FROM menu_structure WHERE key = ?', ('main',))
                row = cursor.fetchone()
                conn.close()
                
                if row:
                    structure = json.loads(row[0])
                else:
                    structure = {}
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(structure).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Save config to database
        elif parsed_path.path == '/api/save-config':
            try:
                data = json.loads(post_data.decode('utf-8'))
                
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                for key, value in data.items():
                    cursor.execute('INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)',
                                  (key, json.dumps(value) if isinstance(value, (dict, list)) else str(value)))
                conn.commit()
                conn.close()
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success'}).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Load config from database
        elif parsed_path.path == '/api/load-config':
            try:
                conn = sqlite3.connect(str(DB_PATH))
                cursor = conn.cursor()
                cursor.execute('SELECT key, value FROM config')
                rows = cursor.fetchall()
                conn.close()
                
                config = {}
                for key, value in rows:
                    try:
                        config[key] = json.loads(value)
                    except:
                        config[key] = value
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(config).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Run test
        elif parsed_path.path.startswith('/api/run-test/'):
            try:
                test_id = parsed_path.path.split('/')[-1]
                
                # Run Maven test
                result = subprocess.run(
                    [resolve_maven_cmd(), 'test'],
                    capture_output=True,
                    text=True,
                    cwd='.'
                )
                
                response = {
                    'status': 'success' if result.returncode == 0 else 'failed',
                    'exitCode': result.returncode
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Update configuration
        elif parsed_path.path == '/api/update-config':
            try:
                data = json.loads(post_data.decode('utf-8'))
                
                # Update config.properties file
                config_path = Path("config.properties")
                if config_path.exists():
                    lines = config_path.read_text().split('\n')
                    new_lines = []
                    
                    for line in lines:
                        if '=' in line:
                            key = line.split('=')[0].strip()
                            if key == 'base.url':
                                new_lines.append(f'base.url={data.get("baseUrl", "")}')
                            elif key == 'user.username':
                                new_lines.append(f'user.username={data.get("username", "")}')
                            elif key == 'user.password':
                                new_lines.append(f'user.password={data.get("password", "")}')
                            elif key == 'timeout.default':
                                new_lines.append(f'timeout.default={data.get("timeout", 30)}')
                            elif key == 'environment':
                                new_lines.append(f'environment={data.get("environment", "test")}')
                            elif key == 'browser.type':
                                new_lines.append(f'browser.type={data.get("browser", "chromium")}')
                            else:
                                new_lines.append(line)
                        else:
                            new_lines.append(line)
                    
                    config_path.write_text('\n'.join(new_lines))
                
                response = {'status': 'success'}
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Run single test
        elif parsed_path.path == '/api/run-single-test':
            try:
                data = json.loads(post_data.decode('utf-8'))
                test_id = data.get('testId')
                
                # In a real implementation, you would:
                # 1. Load the test from storage
                # 2. Generate the Java test file
                # 3. Compile and run the specific test
                # 4. Return results
                
                # For now, simulate execution
                test_command = [
                    resolve_maven_cmd(),
                    'test', 
                    f'-Dtest={test_id}'
                ]
                
                result = subprocess.run(
                    test_command,
                    capture_output=True,
                    text=True,
                    cwd='.'
                )
                
                response = {
                    'status': 'success' if result.returncode == 0 else 'error',
                    'output': result.stdout,
                    'error': result.stderr if result.returncode != 0 else None
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Run module tests
        elif parsed_path.path == '/api/run-module-tests':
            try:
                data = json.loads(post_data.decode('utf-8'))
                module_key = data.get('moduleKey')
                
                # In a real implementation, you would:
                # 1. Find all tests for this module
                # 2. Generate a test suite for the module
                # 3. Run all tests in the module
                
                result = subprocess.run(
                    [resolve_maven_cmd(), 'test'],
                    capture_output=True,
                    text=True,
                    cwd='.'
                )
                
                response = {
                    'status': 'success' if result.returncode == 0 else 'error',
                    'output': result.stdout,
                    'error': result.stderr if result.returncode != 0 else None
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Run menu tests
        elif parsed_path.path == '/api/run-menu-tests':
            try:
                data = json.loads(post_data.decode('utf-8'))
                module_key = data.get('moduleKey')
                menu_key = data.get('menuKey')
                
                # In a real implementation, you would:
                # 1. Find all tests for this specific menu
                # 2. Generate a test suite for the menu
                # 3. Run all tests in the menu
                
                result = subprocess.run(
                    [resolve_maven_cmd(), 'test'],
                    capture_output=True,
                    text=True,
                    cwd='.'
                )
                
                response = {
                    'status': 'success' if result.returncode == 0 else 'error',
                    'output': result.stdout,
                    'error': result.stderr if result.returncode != 0 else None
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        # API: Run all tests
        elif parsed_path.path == '/api/run-all-tests':
            try:
                result = subprocess.run(
                    [resolve_maven_cmd(), 'test'],
                    capture_output=True,
                    text=True,
                    cwd='.'
                )
                
                response = {
                    'status': 'success' if result.returncode == 0 else 'failed',
                    'summary': 'Todos os testes passaram' if result.returncode == 0 else 'Alguns testes falharam'
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        
        else:
            self.send_response(404)
            self.end_headers()
    
    def generate_mock_feature(self, module, menu, test_name, description):
        return f"""Feature: {test_name}
  Como usuário do sistema {module}
  Quero realizar a operação de {menu}
  Para validar o funcionamento do sistema

  Scenario: {test_name}
    Given estou logado no sistema
    And navego para {module} > {menu}
    When realizo as ações necessárias
    Then o sistema deve exibir o resultado esperado
"""
    
    def generate_mock_java(self, module, menu, test_name):
        class_name = ''.join(c for c in test_name if c.isalnum()) + 'Test'
        package_name = f"br.com.qasuite.pages.{module.lower().replace(' ', '_')}"
        
        return f"""package {package_name};

import br.com.qasuite.config.BaseTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class {class_name} extends BaseTest {{
  
  @Override
  protected String getTipoTeste() {{
    return "smoke";
  }}

  @Test
  @Tag("smoke")
  public void test{class_name}() {{
    // Implementar teste para {menu}
    System.out.println("Executando teste: {test_name}");
  }}
}}
"""

def main():
    # Initialize database
    print("Inicializando banco de dados...")
    init_db()
    print("Banco de dados inicializado com sucesso!")
    
    with socketserver.TCPServer(("", PORT), GuiHandler) as httpd:
        print("=" * 50)
        print("  QA Agent - GUI Server")
        print("=" * 50)
        print(f"  Acesse: http://localhost:{PORT}")
        print("=" * 50)
        print("  Pressione Ctrl+C para parar")
        print("=" * 50)
        httpd.serve_forever()

if __name__ == "__main__":
    main()
