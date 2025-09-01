import com.formdev.flatlaf.FlatLightLaf; // 引入 FlatLaf（删除这行如果不用）
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GUI {
    public static void startGui() {
        // 设置 FlatLaf 外观（现代风格）
        try {
            UIManager.setLookAndFeel(new FlatLightLaf()); // 也可用 FlatDarkLaf() 切换暗黑模式
        } catch (Exception e) {
            e.printStackTrace();
            // 备用：使用系统风格
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // 在事件调度线程中创建 GUI
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        // 创建主窗口
        JFrame frame = new JFrame("MCServerInfo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 200);
        frame.setLocationRelativeTo(null); // 居中
        frame.setResizable(false); // 可选：禁止缩放

        // 主面板 - 使用 BoxLayout 垂直布局
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);

        // 标题标签
        JLabel titleLabel = new JLabel("MCServerInfo", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(0x2C3E50));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 提示标签
        JLabel tipLabel = new JLabel("请输入服务器地址以继续", SwingConstants.CENTER);
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        tipLabel.setForeground(Color.GRAY);
        tipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 输入框
        PlaceholderTextField textField = new PlaceholderTextField("example：localhost:25565");
        textField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        textField.setMaximumSize(new Dimension(380, 40));
        textField.setAlignmentX(Component.CENTER_ALIGNMENT);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBDC3C7)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // 按钮
        JButton button = new JButton("Get Info");
        button.setFont(new Font("微软雅黑", Font.BOLD, 14));
        button.setBackground(new Color(0x27AE60)); // 深绿色
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(140, 40));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // 手型光标

        // 添加组件 + 间距
        panel.add(titleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(tipLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 16)));
        panel.add(textField);
        panel.add(Box.createRigidArea(new Dimension(0, 16)));
        panel.add(button);

        // 为输入框添加回车监听
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    button.doClick(); // 触发按钮点击事件（推荐）
                }
            }
        });

        // 按钮事件
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = textField.getText().trim();

                if (input.isEmpty() || "example：localhost:25565".equals(input)) {
                    JOptionPane.showMessageDialog(frame, "⚠️ 请输入服务器地址！", "输入错误", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                Main.HostPort hostPort;
                try {
                    hostPort = Main.parseHostPort(input);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "❌ 地址格式错误：\n" + ex.getMessage(),
                            "输入无效", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 获取文本和 JSON 格式结果
                String result = Main.runAsCli(hostPort.host, hostPort.port, false);
                String json = Main.runAsCli(hostPort.host, hostPort.port, true);

                // 创建自定义按钮
                JButton copyButton = new JButton("📋 复制 JSON 到剪贴板");
                copyButton.addActionListener(ev -> {
                    // 将 JSON 复制到系统剪贴板
                    StringSelection selection = new StringSelection(json);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                    // 可选：提示用户已复制
                    JOptionPane.showMessageDialog(frame, "✅ JSON 已复制到剪贴板！", "已复制", JOptionPane.INFORMATION_MESSAGE);
                });

                // 使用 Object[] 定义按钮（注意顺序）
                Object[] options = {"好的", copyButton};

                // 显示自定义选项对话框
                JOptionPane.showOptionDialog(
                        frame,
                        AnsiToHtml.toHtml(result),
                        "服务器信息",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        options,
                        options[0]
                );
            }
        });

        // 添加面板到窗口
        frame.add(panel);
        frame.setVisible(true);
    }
}

class AnsiToHtml {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\[(\\d+)(?:;(\\d+))?m");

    public static String toHtml(String text) {
        if (text == null || text.isEmpty()) return "<html><body></body></html>";

        // 先按 \n 分割文本，逐行处理（保留换行）
        String[] lines = text.split("\n", -1); // -1 保留末尾空行
        StringBuilder html = new StringBuilder("<html><body style='font-family:微软雅黑,sans-serif;'>");

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) html.append("<br>"); // 每行之间加 <br>
            html.append(processLine(lines[i]));
        }

        html.append("</body></html>");
        return html.toString();
    }

    private static String processLine(String line) {
        StringBuilder html = new StringBuilder();
        int pos = 0;
        boolean bold = false;
        String currentColor = "white";

        String[] colors = {
                // 基础颜色 (0-7)
                "#000000", // 0 黑色 → 深灰更清晰: "#222222"
                "#CC0000", // 1 深红 → 更鲜艳
                "#00CC00", // 2 深绿 → 更亮绿
                "#CC7700", // 3 深黄 → 橙棕色，更清晰
                "#0000CC", // 4 深蓝 → 更亮蓝
                "#CC00CC", // 5 深粉 → 更鲜艳
                "#00CCCC", // 6 深青 → 保留
                "#777777", // 7 灰 → 改为中灰，避免太暗或太亮

                // 亮色 (8-15)
                "#555555", // 8 深灰（重置）→ 用于背景文本
                "#FF5555", // 9 亮红 → 保留（警告/错误）
                "#55FF55", // 10 亮绿 → 保留（成功）
                "#FFBB00", // 11 亮黄 → ✅ 推荐！琥珀色，清晰可见
                "#5555FF", // 12 亮蓝 → 改为 "#3366FF" 更清晰
                "#FF55FF", // 13 亮粉 → 改为 "#FF33CC" 更鲜艳
                "#00FFFF", // 14 青蓝 → 保留（Cyan）
                "#FFFFFF"  // 15 白 → 保留（高亮文本）
        };

        Matcher matcher = ANSI_PATTERN.matcher(line);
        while (matcher.find()) {
            // 添加普通文本
            html.append(escapeHtml(line.substring(pos, matcher.start())));

            int code = Integer.parseInt(matcher.group(1));
            int subCode = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : -1;

            if (code == 0) {
                if (bold) html.append("</b>");
                html.append("</font>");
                bold = false;
                currentColor = "white";
            } else if (code == 1) {
                html.append("<b>");
                bold = true;
            } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                int idx = code < 90 ? code - 30 : code - 90 + 8;
                if (idx >= 0 && idx < colors.length) {
                    currentColor = colors[idx];
                }
                html.append(String.format("<font color='%s'>", currentColor));
            } else if (subCode >= 90 && subCode <= 97) {
                int idx = subCode - 90 + 8;
                if (idx >= 0 && idx < colors.length) {
                    currentColor = colors[idx];
                }
                html.append(String.format("<font color='%s'>", currentColor));
            } else {
                html.append(String.format("<font color='%s'>", currentColor));
            }

            pos = matcher.end();
        }

        // 添加最后一段文本
        html.append(escapeHtml(line.substring(pos)));

        // 闭合标签
        if (bold) html.append("</b>");
        if (!currentColor.equals("white")) html.append("</font>");

        return html.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "<")
                .replace(">", ">")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}