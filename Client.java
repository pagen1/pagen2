package client;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class Client {

    private static final int PORT = 123; // Cổng nhận UDP
    private static final String MULTICAST_ADDRESS = "239.255.0.1"; // Địa chỉ multicast
    private static final String RECEIVED_DATA_FOLDER = "F:\\ct2_Thay hau\\received_data"; // Thư mục lưu khung hình
    private static final String VIDEO_OUTPUT_FOLDER = "F:\\ct2_Thay hau\\videoxuat"; // Thư mục lưu video xuất
    private static final int BUFFER_SIZE = 65000; // Kích thước buffer nhận dữ liệu
    private static boolean receiving = true; // Cờ để dừng nhận gói tin

    public static void main(String[] args) {
        // Tạo một thread để nhận dữ liệu và lưu khung hình
        Thread receivingThread = new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(PORT)) {
                // Tham gia vào nhóm multicast sử dụng biến MULTICAST_ADDRESS
                InetAddress groupAddress = InetAddress.getByName(MULTICAST_ADDRESS);
                socket.joinGroup(groupAddress);

                byte[] buffer = new byte[BUFFER_SIZE];
                File folder = new File(RECEIVED_DATA_FOLDER);
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                int frameCount = 0;
                System.out.println("Đang nhận khung hình...");

                while (receiving) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    if (packet.getLength() < 8) {
                        
                        continue; // Bỏ qua gói tin không hợp lệ
                    }

                    // Đọc metadata (ID khung hình và số thứ tự gói tin)
                    ByteBuffer metaBuffer = ByteBuffer.wrap(packet.getData(), 0, 8);
                    int frameId = metaBuffer.getInt();
                    int sequenceNumber = metaBuffer.getInt();

                    System.out.println("Đã nhận metadata: frameId = " + frameId + ", sequenceNumber = " + sequenceNumber);

                    // Lưu mỗi khung hình vào thư mục với tên frame_ + số thứ tự
                    String fileName = RECEIVED_DATA_FOLDER + "\\frame_" + (frameCount++) + ".jpg";
                    try (FileOutputStream fos = new FileOutputStream(fileName)) {
                        fos.write(packet.getData(), 8, packet.getLength() - 8); // Bỏ qua phần metadata
                    }

                    System.out.println("Đã lưu khung hình: " + fileName);
                }

                System.out.println("Nhận khung hình xong!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        receivingThread.start();

        // Thêm logic theo dõi khi nào thread nhận gói tin kết thúc
        Thread monitorThread = new Thread(() -> {
            try {
                // Kiểm tra nếu còn khung hình trong thư mục hoặc cờ receiving chưa được thay đổi
                while (receiving) {
                    // Kiểm tra tình trạng nhận khung hình (đảm bảo quá trình nhận được hoàn thành)
                    Thread.sleep(1000); // Kiểm tra mỗi giây
                }
                // Khi quá trình nhận xong, tiến hành tạo video
                convertFramesToVideo(RECEIVED_DATA_FOLDER, VIDEO_OUTPUT_FOLDER + "\\output_video.mp4");
                System.out.println("Video đã được tạo và lưu tại: " + VIDEO_OUTPUT_FOLDER + "\\output_video.mp4");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        monitorThread.start();

        // Đảm bảo thread nhận gói tin hoàn tất trước khi dừng
        try {
            Thread.sleep(30000); // 30 giây (thay đổi theo yêu cầu)
            receiving = false; // Dừng nhận gói tin sau khi thời gian trôi qua
            receivingThread.join(); // Đợi thread kết thúc
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Chuyển đổi các khung hình đã lưu thành video
    private static void convertFramesToVideo(String framesFolder, String outputVideoFile) {
        File folder = new File(framesFolder);
        File[] frameFiles = folder.listFiles((dir, name) -> name.endsWith(".jpg"));

        if (frameFiles == null || frameFiles.length == 0) {
            System.out.println("Không có khung hình nào để tạo video!");
            return;
        }

        try {
            // Tạo FFmpegFrameRecorder để ghi video
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputVideoFile, 640, 480);
            recorder.setFormat("mp4");
            recorder.setFrameRate(30); // Cập nhật theo tốc độ khung hình bạn muốn

            recorder.start();

            // Đọc từng khung hình và ghi vào video
            for (File frameFile : frameFiles) {
                BufferedImage img = ImageIO.read(frameFile);
                if (img != null) {
                    // Chuyển đổi BufferedImage thành Frame
                    Frame frame = convertToFrame(img);
                    recorder.record(frame);
                }
            }

            recorder.stop();
            System.out.println("Video đã được tạo và lưu tại: " + outputVideoFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Chuyển đổi BufferedImage thành Frame (JavaCV)
    private static Frame convertToFrame(BufferedImage img) {
        Java2DFrameConverter converter = new Java2DFrameConverter();
        return converter.convert(img);
    }
}
