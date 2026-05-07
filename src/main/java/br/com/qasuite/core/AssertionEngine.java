package br.com.qasuite.core;

import br.com.qasuite.domain.StepResult;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Motor de validações e assertions
 */
public class AssertionEngine {

    private final Page page;
    private final ElementDetector detector;

    public AssertionEngine(Page page) {
        this.page = page;
        this.detector = new ElementDetector(page);
    }

    /**
     * Executa assertion com fallback automático
     */
    public void assertWithFallback(String type, String target, String expectedValue, StepResult result) throws Exception {
        List<String> errors = new ArrayList<>();

        // Tenta assertion exato
        try {
            assertExact(type, target, expectedValue);
            result.setElementUsed(target);
            return;
        } catch (Exception e) {
            errors.add("Exact match failed: " + e.getMessage());
        }

        // Auto-healing: tenta variações do target
        List<String> variations = generateVariations(target);
        for (String variation : variations) {
            try {
                assertExact(type, variation, expectedValue);
                result.setElementUsed(variation);
                System.out.println("[AssertionEngine] Auto-healing found match: " + variation);
                return;
            } catch (Exception e) {
                errors.add("Variation '" + variation + "' failed: " + e.getMessage());
            }
        }

        // Se nenhuma variação funcionou, lança erro
        throw new AssertionError("All assertion attempts failed: " + String.join("; ", errors));
    }

    /**
     * Assertion exato
     */
    private void assertExact(String type, String target, String expectedValue) throws Exception {
        switch (type.toLowerCase()) {
            case "text_visible":
            case "text":
                assertTextVisible(target, expectedValue);
                break;

            case "element_exists":
            case "exists":
                assertElementExists(target);
                break;

            case "field_value":
            case "value":
                assertFieldValue(target, expectedValue);
                break;

            case "url_contains":
            case "url":
                assertUrlContains(target);
                break;

            case "element_count":
                assertElementCount(target, expectedValue);
                break;

            default:
                throw new UnsupportedOperationException("Assertion type not supported: " + type);
        }
    }

    /**
     * Valida que texto está visível na página
     */
    private void assertTextVisible(String target, String expectedValue) throws Exception {
        String searchText = expectedValue != null ? expectedValue : target;

        // Aguarda carregamento
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        // Busca texto na página
        boolean found = false;

        // Tenta diferentes seletores
        String[] strategies = {
            "text='" + searchText + "'",
            "text='" + searchText + "' i",
            ":text-is('" + searchText + "')",
            ":text-matches('" + Pattern.quote(searchText) + "', 'i')"
        };

        for (String strategy : strategies) {
            try {
                Locator locator = page.locator(strategy);
                if (locator.count() > 0 && locator.first().isVisible()) {
                    found = true;
                    break;
                }
            } catch (Exception e) {
                // Continua tentando
            }
        }

        // Busca parcial se exato não encontrou
        if (!found) {
            Locator allElements = page.locator("*:visible");
            int count = (int) allElements.count();

            for (int i = 0; i < Math.min(count, 200); i++) {
                try {
                    String text = allElements.nth(i).textContent();
                    if (text != null && text.toLowerCase().contains(searchText.toLowerCase())) {
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    // Ignora
                }
            }
        }

        if (!found) {
            throw new AssertionError("Text not visible: '" + searchText + "'");
        }

        System.out.println("[AssertionEngine] Text visible: '" + searchText + "'");
    }

    /**
     * Valida que elemento existe
     */
    private void assertElementExists(String target) throws Exception {
        String[] strategies = {
            "text='" + target + "'",
            "text='" + target + "' i",
            "[aria-label='" + target + "']",
            "[placeholder='" + target + "']",
            "[name='" + target + "']",
            "#" + target,
            "[id='" + target + "']"
        };

        for (String strategy : strategies) {
            try {
                Locator locator = page.locator(strategy);
                if (locator.count() > 0) {
                    System.out.println("[AssertionEngine] Element exists: '" + target + "'");
                    return;
                }
            } catch (Exception e) {
                // Continua
            }
        }

        throw new AssertionError("Element does not exist: '" + target + "'");
    }

    /**
     * Valida valor de campo
     */
    private void assertFieldValue(String fieldName, String expectedValue) throws Exception {
        String[] selectors = {
            "input[name='" + fieldName + "']",
            "input[id='" + fieldName + "']",
            "input[placeholder='" + fieldName + "']",
            "textarea[name='" + fieldName + "']",
            "textarea[id='" + fieldName + "']",
            "select[name='" + fieldName + "']",
            "select[id='" + fieldName + "']"
        };

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    String actualValue = locator.inputValue();
                    if (actualValue != null && actualValue.equals(expectedValue)) {
                        System.out.println("[AssertionEngine] Field '" + fieldName + "' has value '" + expectedValue + "'");
                        return;
                    } else {
                        throw new AssertionError("Field '" + fieldName + "' expected '" + expectedValue + "' but found '" + actualValue + "'");
                    }
                }
            } catch (AssertionError e) {
                throw e; // Re-lança AssertionError
            } catch (Exception e) {
                // Continua tentando outros seletores
            }
        }

        throw new AssertionError("Field not found: '" + fieldName + "'");
    }

    /**
     * Valida URL contém texto
     */
    private void assertUrlContains(String expectedText) throws Exception {
        String currentUrl = page.url();
        if (!currentUrl.toLowerCase().contains(expectedText.toLowerCase())) {
            throw new AssertionError("URL does not contain '" + expectedText + "'. Current: " + currentUrl);
        }
        System.out.println("[AssertionEngine] URL contains: '" + expectedText + "'");
    }

    /**
     * Valida contagem de elementos
     */
    private void assertElementCount(String selector, String expectedCount) throws Exception {
        int expected = Integer.parseInt(expectedCount);
        Locator locator = page.locator(selector);
        int actual = (int) locator.count();

        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " elements but found " + actual);
        }
        System.out.println("[AssertionEngine] Element count matches: " + expected);
    }

    /**
     * Gera variações de texto para auto-healing
     */
    private List<String> generateVariations(String original) {
        List<String> variations = new ArrayList<>();
        variations.add(original);

        // Variações case
        variations.add(original.toLowerCase());
        variations.add(original.toUpperCase());

        // Remove acentos
        String withoutAccents = original
                .replaceAll("[áàãâä]", "a")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[íìîï]", "i")
                .replaceAll("[óòõôö]", "o")
                .replaceAll("[úùûü]", "u")
                .replaceAll("[ç]", "c")
                .replaceAll("[ÁÀÃÂÄ]", "A")
                .replaceAll("[ÉÈÊË]", "E")
                .replaceAll("[ÍÌÎÏ]", "I")
                .replaceAll("[ÓÒÕÔÖ]", "O")
                .replaceAll("[ÚÙÛÜ]", "U")
                .replaceAll("[Ç]", "C");
        variations.add(withoutAccents);

        // Palavras individuais (para textos longos)
        if (original.contains(" ")) {
            String[] words = original.split("\\s+");
            for (String word : words) {
                if (word.length() > 3) {
                    variations.add(word);
                }
            }
        }

        return variations;
    }
}
