package br.com.qasuite.server;

import br.com.qasuite.config.Context7Config;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Context7CliService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_CAPTURE_CHARS = 200_000;

    private final Context7Config config;

    public Context7CliService() {
        this.config = new Context7Config();
    }

    public JsonObject status() {
        JsonObject res = new JsonObject();
        res.addProperty("configured", config.isValid());
        res.addProperty("apiKey", config.getMaskedApiKey());
        return res;
    }

    public JsonObject library(String name, String query) {
        return run(DEFAULT_TIMEOUT_SECONDS, buildArgs("library", name, query));
    }

    public JsonObject docs(String libraryId, String query, boolean research) {
        List<String> args = new ArrayList<>(buildArgs("docs", libraryId, query));
        if (research) args.add("--research");
        return run(DEFAULT_TIMEOUT_SECONDS, args);
    }

    private List<String> buildArgs(String command, String a1, String a2) {
        List<String> args = new ArrayList<>();
        args.add(npxExecutable());
        args.add("ctx7@latest");
        args.add(command);
        args.add(a1);
        args.add(a2);
        return args;
    }

    private String npxExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "npx.cmd" : "npx";
    }

    private JsonObject run(int timeoutSeconds, List<String> args) {
        JsonObject res = new JsonObject();
        res.addProperty("configured", config.isValid());

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(Paths.get(".").toFile());
        if (config.isValid()) {
            pb.environment().put("CONTEXT7_API_KEY", config.getApiKey());
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        boolean timedOut = false;
        int exitCode = -1;
        boolean stdoutTruncated = false;
        boolean stderrTruncated = false;

        try {
            Process p = pb.start();

            StreamCapture outCapture = new StreamCapture(p.getInputStream(), stdout, MAX_CAPTURE_CHARS);
            StreamCapture errCapture = new StreamCapture(p.getErrorStream(), stderr, MAX_CAPTURE_CHARS);
            Thread t1 = new Thread(outCapture);
            Thread t2 = new Thread(errCapture);
            t1.start();
            t2.start();

            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                timedOut = true;
                p.destroyForcibly();
            } else {
                exitCode = p.exitValue();
            }

            t1.join(TimeUnit.SECONDS.toMillis(2));
            t2.join(TimeUnit.SECONDS.toMillis(2));

            stdoutTruncated = outCapture.isTruncated();
            stderrTruncated = errCapture.isTruncated();
        } catch (Exception e) {
            stderr.append(e.getMessage() == null ? e.toString() : e.getMessage());
        }

        res.addProperty("exitCode", exitCode);
        res.addProperty("timedOut", timedOut);
        res.addProperty("stdoutTruncated", stdoutTruncated);
        res.addProperty("stderrTruncated", stderrTruncated);
        res.addProperty("stdout", stripAnsi(stdout.toString()));
        res.addProperty("stderr", stripAnsi(stderr.toString()));
        return res;
    }

    private String stripAnsi(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
    }

    private static class StreamCapture implements Runnable {
        private final InputStream in;
        private final StringBuilder out;
        private final int maxChars;
        private volatile boolean truncated = false;

        StreamCapture(InputStream in, StringBuilder out, int maxChars) {
            this.in = in;
            this.out = out;
            this.maxChars = maxChars;
        }

        boolean isTruncated() {
            return truncated;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = br.read(buf)) != -1) {
                    if (out.length() < maxChars) {
                        int remaining = maxChars - out.length();
                        if (n <= remaining) {
                            out.append(buf, 0, n);
                        } else {
                            out.append(buf, 0, remaining);
                            truncated = true;
                        }
                    } else {
                        truncated = true;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}
