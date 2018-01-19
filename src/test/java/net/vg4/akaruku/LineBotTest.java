package net.vg4.akaruku;

import static junit.framework.TestCase.fail;
import static org.springframework.http.ResponseEntity.ok;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class LineBotTest {
    LineBot lineBot = new LineBot();

    @Test
    void revise() {
        int[] lookupTable1 = new int[256];
        double gammaVal1 = 2.9;
        for (int i = 0; i < 256; i++) {
            lookupTable1[i] = (int) Math.round(255 * Math.pow(((double) i / 255), 1 / gammaVal1));
        }
        try {
//            Path before = createTempFile("jpg");
//            Path after = createTempFile("png");
            BufferedImage orig = createDummyImage(new File("test").toPath());
            Method revise = LineBot.class.getDeclaredMethod("revise", BufferedImage.class, int[].class);
            revise.setAccessible(true);
            BufferedImage bufferedImage = (BufferedImage) revise.invoke(lineBot, orig, lookupTable1);
//            ImageIO.write(bufferedImage, "png", new File(after.toString()));
//            log.info("before {}", before.toString());
//            log.info("after  {}", after.toString());
            int ih = orig.getHeight();
            int iw = orig.getWidth();
            boolean ok = false;
            for (int h = 0; h < ih; h++) {
                for (int w = 0; w < iw; w++) {
                    int iC = orig.getRGB(w, h);
                    int iD = bufferedImage.getRGB(w, h);
                    if (iC != iD) {
                        ok = true;
                        break;
                    }
                }
            }
            if (ok == true) {
                ok("ok");
            } else {
                fail("ng");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("exception occured.");
        }

    }

    private static Path createTempFile(String ext) throws IOException {
        String fileName = LocalDateTime.now().toString() + '-' + UUID.randomUUID().toString() + '.' + ext;
        Path tempFile = Files.createTempDirectory("line-bot-test").resolve(fileName);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private BufferedImage createDummyImage(Path filename) throws IOException {
        BufferedImage bufferedImage = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = bufferedImage.createGraphics();
        graphics2D.setColor(Color.BLUE);
        graphics2D.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
        Font font = new Font("Consolas", Font.PLAIN, 30);
        graphics2D.setFont(font);
        graphics2D.setColor(Color.WHITE);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics2D.drawString("wao", 5, 5);
//        ImageIO.write(bufferedImage, "png", new File(filename.toString()));
        return bufferedImage;
    }
}