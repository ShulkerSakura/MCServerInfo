import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class PlaceholderTextField extends JTextField {
    private String placeholder;

    public PlaceholderTextField(String placeholder) {
        this.placeholder = placeholder;
        // 添加焦点监听器，用于判断是否显示 placeholder
        this.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (getText().isEmpty()) {
                    // 获得焦点时，如果内容为空，不显示 placeholder（由 paintComponent 控制）
                    repaint();
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().isEmpty()) {
                    repaint(); // 失去焦点且为空时显示 placeholder
                }
            }
        });

        // 监听文本变化
        this.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { repaint(); }

            @Override
            public void removeUpdate(DocumentEvent e) { repaint(); }

            @Override
            public void changedUpdate(DocumentEvent e) { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 如果文本为空且没有焦点，绘制 placeholder
        if (placeholder != null && !getText().isEmpty()) {
            return; // 有文本时不画
        }

        if (placeholder != null && getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(getDisabledTextColor()); // 使用灰色类似 placeholder 效果
            FontMetrics fm = g2.getFontMetrics();
            Insets insets = getInsets();
            int x = insets.left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, x, y);
            g2.dispose();
        }
    }
}