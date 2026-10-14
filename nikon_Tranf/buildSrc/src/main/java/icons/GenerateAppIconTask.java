package icons;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用图标生成：读取仓库根 img/logo.png，居中裁方后缩放输出各密度 mipmap。
 * 换图标只需替换 img/logo.png 后重新编译，无需手动出切图。
 */
public class GenerateAppIconTask extends DefaultTask {

    /** 各密度对应的图标边长（px） */
    private static final Map<String, Integer> DENSITIES = new LinkedHashMap<>();

    static {
        DENSITIES.put("mdpi", 48);
        DENSITIES.put("hdpi", 72);
        DENSITIES.put("xhdpi", 96);
        DENSITIES.put("xxhdpi", 144);
        DENSITIES.put("xxxhdpi", 192);
    }

    /** 配置期捕获的路径（String 可被配置缓存序列化；执行期禁止再访问 Task.project） */
    private final String logoPath;
    private final String resRootPath;

    public GenerateAppIconTask() {
        // Gradle 根目录是 nikon_Tranf/，仓库根在其上一级
        this.logoPath = getProject().getRootDir().getParentFile().toPath()
                .resolve("img").resolve("logo.png").toString();
        this.resRootPath = getProject().getProjectDir().toPath()
                .resolve("src").resolve("main").resolve("res").toString();
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public File getLogoFile() {
        return new File(logoPath);
    }

    @OutputFiles
    public List<File> getIconOutputFiles() {
        List<File> files = new ArrayList<>();
        for (String d : DENSITIES.keySet()) {
            files.add(mipmapDir(d).resolve("ic_launcher.png").toFile());
            files.add(mipmapDir(d).resolve("ic_launcher_round.png").toFile());
        }
        return files;
    }

    @TaskAction
    public void generate() {
        File logoFile = new File(logoPath);
        if (!logoFile.exists()) {
            throw new GradleException("找不到应用图标源文件 " + logoFile.getAbsolutePath());
        }
        BufferedImage src;
        try {
            src = ImageIO.read(logoFile);
        } catch (IOException e) {
            throw new GradleException("logo.png 读取失败", e);
        }
        if (src == null) {
            throw new GradleException("logo.png 无法解析为图片");
        }
        int side = Math.min(src.getWidth(), src.getHeight());
        for (Map.Entry<String, Integer> entry : DENSITIES.entrySet()) {
            File resDir = mipmapDir(entry.getKey()).toFile();
            resDir.mkdirs();
            int size = entry.getValue();
            // 方形图标：居中裁正方形后缩放
            BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = square.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, size, size,
                    (src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                    (src.getWidth() + side) / 2, (src.getHeight() + side) / 2, null);
            g.dispose();
            // 圆形图标（启动器使用 roundIcon 时显示）：方形基础上加圆形蒙版
            BufferedImage round = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            g = round.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.clip(new Ellipse2D.Float(0, 0, size, size));
            g.drawImage(square, 0, 0, null);
            g.dispose();
            try {
                ImageIO.write(square, "png", new File(resDir, "ic_launcher.png"));
                ImageIO.write(round, "png", new File(resDir, "ic_launcher_round.png"));
            } catch (IOException e) {
                throw new GradleException("图标写出失败 " + resDir, e);
            }
        }
    }

    private Path mipmapDir(String density) {
        return java.nio.file.Paths.get(resRootPath).resolve("mipmap-" + density);
    }
}
