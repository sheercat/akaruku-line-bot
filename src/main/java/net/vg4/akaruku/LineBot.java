package net.vg4.akaruku;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.io.ByteStreams;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@LineMessageHandler
public class LineBot {
    static int[] lookupTable1;
    static int[] lookupTable2;
    static double gammaVal1 = 2.9;
    static double gammaVal2 = 1.5;

    static {
        lookupTable1 = new int[256];
        for (int i = 0; i < 256; i++) {
            lookupTable1[i] = (int) Math.round(255 * Math.pow(((double) i / 255), 1 / gammaVal1));
        }
        lookupTable2 = new int[256];
        for (int i = 0; i < 256; i++) {
            lookupTable2[i] = (int) Math.round(255 * Math.pow(((double) i / 255), 1 / gammaVal2));
        }
    }

    @Autowired
    LineMessagingClient lineMessagingClient;

    @EventMapping
    public TextMessage handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
        System.out.println("event: " + event);
        return new TextMessage(event.getMessage().getText());
    }

    @EventMapping
    public void handleDefaultMessageEvent(Event event) {
        System.out.println("event: " + event);
    }

    @EventMapping
    public void handleImageMessageEvent(MessageEvent<ImageMessageContent> event) throws IOException {
        handleHeavyContent(
                event.getReplyToken(),
                event.getMessage().getId(),
                responseBody -> {
                    List<Message> messages = new ArrayList<Message>();
                    DownloadedContent jpg = saveContent("jpg", responseBody);
                    BufferedImage orig;
                    try {
                        orig = ImageIO.read(new File(jpg.path.toString()));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    try {
                        DownloadedContent rvd = createTempFile("jpg");
                        BufferedImage revised = revise(orig, this.lookupTable1);
                        ImageIO.write(revised, "jpg", new File(rvd.path.toString()));
                        messages.add(new ImageMessage(rvd.getUri(), rvd.getUri()));
                        log.info(rvd.path.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        DownloadedContent rvd = createTempFile("jpg");
                        BufferedImage revised = revise(orig, this.lookupTable2);
                        ImageIO.write(revised, "jpg", new File(rvd.path.toString()));
                        messages.add(new ImageMessage(rvd.getUri(), rvd.getUri()));
                        log.info(rvd.path.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    reply(((MessageEvent) event).getReplyToken(), messages);
                });
    }

    public BufferedImage revise(BufferedImage image, int[] lookup) {
        BufferedImage alteredImage = new BufferedImage(image.getWidth(),
                                                       image.getHeight(),
                                                       image.getType()
        );
//                                                       BufferedImage.TYPE_INT_ARGB);
        int iCd = 0;

        for (int h = 0; h < image.getHeight(); h++) {
            for (int w = 0; w < image.getWidth(); w++) {
                int iC = image.getRGB(w, h);

                int iB = iC & 0x00ff;
                int iG = (iC >> 8) & 0x00ff;
                int iR = (iC >> 16) & 0x00ff;
                int iA = (iC >> 24) & 0x00ff;

                int iRd = lookup[iR];
                int iGd = lookup[iG];
                int iBd = lookup[iB];

                iCd = (iA << 24) + (iRd << 16) + (iGd << 8) + iBd;

                alteredImage.setRGB(w, h, iCd);
            }
        }
        return alteredImage;
    }

    private void handleHeavyContent(String replyToken, String messageId,
                                    Consumer<MessageContentResponse> messageConsumer) {
        final MessageContentResponse response;
        try {
            response = lineMessagingClient.getMessageContent(messageId)
                                          .get();
        } catch (InterruptedException | ExecutionException e) {
            reply(replyToken, new TextMessage("Cannot get image: " + e.getMessage()));
            throw new RuntimeException(e);
        }
        messageConsumer.accept(response);
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        try {
            BotApiResponse apiResponse = lineMessagingClient
                    .replyMessage(new ReplyMessage(replyToken, messages))
                    .get();
            log.info("Sent messages: {}", apiResponse);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static DownloadedContent saveContent(String ext, MessageContentResponse responseBody) {
        log.info("Got content-type: {}", responseBody);

        DownloadedContent tempFile = createTempFile(ext);
        try (OutputStream outputStream = Files.newOutputStream(tempFile.path)) {
            ByteStreams.copy(responseBody.getStream(), outputStream);
            log.info("Saved {}: {}", ext, tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DownloadedContent createTempFile(String ext) {
        String fileName = LocalDateTime.now().toString() + '-' + UUID.randomUUID().toString() + '.' + ext;
        Path tempFile = App.downloadedContentDir.resolve(fileName);
        tempFile.toFile().deleteOnExit();
        return new DownloadedContent(tempFile, createUri("/downloaded/" + tempFile.getFileName()));
    }

    @Value
    public static class DownloadedContent {
        Path path;
        String uri;
    }

    private static String createUri(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                                          .path(path).build()
                                          .toUriString();
    }

    private void system(String... args) {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        try {
            Process start = processBuilder.start();
            int i = start.waitFor();
            log.info("result: {} =>  {}", Arrays.toString(args), i);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            log.info("Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

}
