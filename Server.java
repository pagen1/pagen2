package server;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class Server {
    public static void main(String[] args) {
        final int PORT = 123; // Cổng UDP
        final String VIDEO_PATH = Paths.get("F:\\ct2_Thay hau\\Download.mp4").toAbsolutePath().toString();
        final int MAX_PACKET_SIZE = 65000; // Kích thước tối đa gói UDP

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress groupAddress = InetAddress.getByName("239.255.0.1"); // Địa chỉ multicast
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(VIDEO_PATH);
            grabber.start(); // Bắt đầu đọc video

            Frame frame;
            Java2DFrameConverter converter = new Java2DFrameConverter();
            System.out.println("Đang phát video...");

            int frameId = 0; // ID khung hình

            while ((frame = grabber.grab()) != null) {
                // Chuyển khung hình thành BufferedImage
                BufferedImage bufferedImage = converter.convert(frame);
                if (bufferedImage != null) {
                    // Chuyển BufferedImage thành byte[]
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, "jpg", baos); // Đảm bảo định dạng là JPG
                    byte[] imageBytes = baos.toByteArray();

                    // Gửi độ dài khung hình trước
                    byte[] lengthBytes = ByteBuffer.allocate(4).putInt(imageBytes.length).array();
                    DatagramPacket lengthPacket = new DatagramPacket(lengthBytes, lengthBytes.length, groupAddress, PORT);
                    socket.send(lengthPacket);

                    // Chia nhỏ dữ liệu ảnh và gửi
                    int offset = 0;
                    int sequenceNumber = 0; // Số thứ tự gói tin trong khung hình
                    while (offset < imageBytes.length) {
                        int packetSize = Math.min(MAX_PACKET_SIZE - 8, imageBytes.length - offset); // Chừa 8 byte cho metadata
                        byte[] packetData = new byte[packetSize + 8]; // Tổng kích thước gói tin

                        // Gắn ID khung hình và sequence number vào đầu gói tin
                        ByteBuffer.wrap(packetData, 0, 8)
                                .putInt(frameId) // ID khung hình
                                .putInt(sequenceNumber++); // Số thứ tự gói tin

                        // Chuyển phần dữ liệu ảnh vào gói tin
                        System.arraycopy(imageBytes, offset, packetData, 8, packetSize);

                        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, groupAddress, PORT);
                        socket.send(packet);
                        offset += packetSize;
                    }

                    System.out.println("Đã gửi một khung hình: " + imageBytes.length + " bytes");

                    // Điều chỉnh tốc độ phát video
                    TimeUnit.MILLISECONDS.sleep((long) (2000 / grabber.getFrameRate()));
                }

                frameId++; // Tăng ID khung hình
            }

            grabber.stop();
            System.out.println("Video phát xong.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
