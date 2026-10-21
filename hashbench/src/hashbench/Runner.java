package hashbench;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Khởi chạy tiến trình con để cách ly lượt chạy benchmark. */
public final class Runner {
    private Runner() {
    }

    /** Chạy bảng stub tối đa 60 giây và trả về trạng thái kết thúc. */
    public static String run(RunConfig config) throws IOException, InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config không được null");
        }
        if (config.capacity() <= 0) {
            throw new IllegalArgumentException("Dung lượng bảng không hợp lệ");
        }

        String javaName = "java";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javaName = "java.exe";
        }
        String javaPath = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + javaName;

        ProcessBuilder builder = new ProcessBuilder(
                javaPath,
                "-Xmx2g",
                "-cp",
                System.getProperty("java.class.path"),
                Main.class.getName(),
                "--stub-child");
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process child = builder.start();
        boolean finished;
        try {
            finished = child.waitFor(60L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            child.destroyForcibly();
            throw exception;
        }
        if (!finished) {
            child.destroyForcibly();
            return "timeout";
        }
        if (child.exitValue() != 0) {
            return "error_exit_" + child.exitValue();
        }
        return "ok";
    }
}
