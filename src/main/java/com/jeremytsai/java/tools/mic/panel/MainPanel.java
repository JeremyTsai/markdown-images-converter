package com.jeremytsai.java.tools.mic.panel;

import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main panel of the program.
 *
 * @author Jeremy Tsai
 * @version 1.0
 * @since 2024-02-14 14:56
 */
public class MainPanel extends JPanel implements ActionListener {

    public static final String BTN_SELECT_SRC_DIRECTORY = "btn-select-src-directory";
    public static final String BTN_START = "btn-start";
    public static final String PATTERN_MD_IMG = "(.*)!\\[(.*?)]\\((.*?)\\)";
    public static final String PATTERN_LOCAL_IMG = ".*\\\\(.*)\\\\(.*)$";
    public static final String PATTERN_URL = "^(http|https|ftp)://([a-zA-Z0-9.-]+)(:[0-9]+)?(/.*)?/(.*)(\\..*)$";
    private final SpringLayout layout;
    private final Pattern patternMDImg;
    private final Pattern patternUrl;

    private JLabel lSrcDirectory;
    private JTextField tfSrcDirectory;
    private JButton btnSrcDirectory;
    private JFileChooser fcSrcDirectory;

    private JTextArea taOutput;
    private JScrollPane spOutput;

    private JButton btnStart;

    public MainPanel() {
        layout = new SpringLayout();
        patternMDImg = Pattern.compile(PATTERN_MD_IMG);
        patternUrl = Pattern.compile(PATTERN_URL);
        setLayout(layout);
        setPreferredSize(new Dimension(1152, 648));
        initComponents();
        layoutComponents();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case BTN_SELECT_SRC_DIRECTORY -> selectFile();
            case BTN_START -> startConvert();
        }
    }

    /**
     * Start convert.
     */
    private void startConvert() {
        if (tfSrcDirectory.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a folder.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<File> fileList = new ArrayList<>();
        for (Object obj : FileUtils.listFiles(fcSrcDirectory.getSelectedFile(), new String[]{"md"}, true)) {
            if (!(obj instanceof File)) continue;
            fileList.add((File) obj);
        }
        fileList.parallelStream().forEach(this::handleMD);
        JOptionPane.showMessageDialog(this, "Done.");
    }

    /**
     * Select file.
     */
    private void selectFile() {
        int ret = fcSrcDirectory.showOpenDialog(this);
        if (JFileChooser.APPROVE_OPTION == ret) {
            tfSrcDirectory.setText(fcSrcDirectory.getSelectedFile().getPath());
        }
    }

    /**
     * Process each .md file.
     * @param file .md file
     */
    private void handleMD(File file) {
        try {
            String imgDirectory = file.getPath();
            imgDirectory = imgDirectory.substring(0, imgDirectory.lastIndexOf("."));
            Path imgPath = Path.of(imgDirectory);
            boolean delete = !Files.exists(imgPath);
            if (delete) Files.createDirectory(imgPath);
            String[] lines = Files.readAllLines(file.toPath()).toArray(new String[]{});
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String ret = processImgLinks(imgPath, line);
                if (ret == null) continue;
                if (delete) delete = false;
                lines[i] = line.replaceAll(PATTERN_MD_IMG, "![$2](" +
                        ret.replaceAll(PATTERN_LOCAL_IMG, "$1/$2") +
                        ")");
            }
            if (delete) Files.delete(imgPath);
            FileUtils.writeLines(file, List.of(lines));
        } catch (IOException e) {
            output(e.getMessage());
        }
    }

    /**
     * Process image links.
     *
     * @param imgPath Image path.
     * @param s    Every line of the document.
     * @return The result of the processing.
     */
    private String processImgLinks(Path imgPath, String s) {
        Matcher matcher = patternMDImg.matcher(s);
        if (!matcher.find()) return null;
        String url = matcher.group(3);
        Matcher urlMatch = patternUrl.matcher(url);
        if (!urlMatch.find()) return null;
        String extension = null;
        try {
            String fileName = urlMatch.group(5);
            if (fileName.contains("%")) fileName = urlDecode(fileName);
            try { extension = urlMatch.group(6); } catch (Exception ignore) { }
            return downImg(imgPath, fileName, extension, url);
        } catch (Exception ignore) {
            output(String.format("url: %s, unable to process. Unable to obtain the file name or extension from the image link.", url));
            return null;
        }
    }

    private String downImg(Path imgPath, String fileName, String extension, String url) {
        try {
            URLConnection urlConnection = URI.create(url).toURL().openConnection();
            //noinspection SpellCheckingInspection
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0");
            if (extension == null) extension = urlConnection.getContentType().split("/")[1];
            String savePath = getImageSavePath(imgPath, fileName, extension);
            InputStream inputStream = urlConnection.getInputStream();
            try (
                    BufferedInputStream bis = new BufferedInputStream(inputStream);
                    FileOutputStream fos = new FileOutputStream(savePath)
            ) {
                byte[] data = new byte[8192];
                int count;
                while ((count = bis.read(data)) != -1) {
                    fos.write(data, 0, count);
                }
            }
            return savePath;
        } catch (Exception ignore) {
            output(String.format("The image from URL: %s cannot be downloaded.", url));
        }
        return null;
    }

    private String getImageSavePath(Path imgPath, String fileName, String extension) {
        return imgPath.toString() + File.separator + fileName + extension;
    }


    /**
     * Initialize components.
     */
    private void initComponents() {
        // First row.
        lSrcDirectory = new JLabel("Selected Directory: ");
        tfSrcDirectory = new JTextField();
        tfSrcDirectory.setEditable(false);
        btnSrcDirectory = new JButton("Select");
        btnSrcDirectory.setActionCommand(BTN_SELECT_SRC_DIRECTORY);
        btnSrcDirectory.addActionListener(this);
        fcSrcDirectory = new JFileChooser();
        fcSrcDirectory.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        add(lSrcDirectory);
        add(tfSrcDirectory);
        add(btnSrcDirectory);

        // Second row.
        taOutput = new JTextArea(5, 30);
        taOutput.setEditable(false);
        spOutput = new JScrollPane(taOutput);
        add(spOutput);

        // Third row.
        btnStart = new JButton("Start");
        btnStart.setActionCommand(BTN_START);
        btnStart.addActionListener(this);
        add(btnStart);
    }

    /**
     * Layout components.
     */
    private void layoutComponents() {
        // Layout first row.
        // Layout lSrcDirectory.
        layout.putConstraint(SpringLayout.WEST, lSrcDirectory, 16, SpringLayout.WEST, this);
        layout.putConstraint(SpringLayout.NORTH, lSrcDirectory, 16, SpringLayout.NORTH, this);
        // Layout tfSrcDirectory.
        layout.putConstraint(SpringLayout.NORTH, tfSrcDirectory, 0, SpringLayout.NORTH, lSrcDirectory);
        layout.putConstraint(SpringLayout.WEST, tfSrcDirectory, 8, SpringLayout.EAST, lSrcDirectory);
        layout.putConstraint(SpringLayout.EAST, tfSrcDirectory, -8, SpringLayout.WEST, btnSrcDirectory);
        // Layout btnSrcDirectory.
        layout.putConstraint(SpringLayout.NORTH, btnSrcDirectory, 0, SpringLayout.NORTH, lSrcDirectory);
        layout.putConstraint(SpringLayout.EAST, btnSrcDirectory, -16, SpringLayout.EAST, this);

        // Layout second row.
        SpringLayout.Constraints spOutputConstraints = layout.getConstraints(spOutput);
        spOutputConstraints.setY(Spring.sum(Spring.constant(16), layout.getConstraints(lSrcDirectory).getConstraint(SpringLayout.SOUTH)));
        spOutputConstraints.setX(Spring.constant(16));
        spOutputConstraints.setConstraint(SpringLayout.EAST, Spring.sum(Spring.constant(-16), layout.getConstraint(SpringLayout.EAST, this)));
        spOutputConstraints.setConstraint(SpringLayout.SOUTH, Spring.sum(Spring.constant(-16), layout.getConstraint(SpringLayout.NORTH, btnStart)));

        // Layout third row.
        layout.putConstraint(SpringLayout.SOUTH, btnStart, -16, SpringLayout.SOUTH, this);
        layout.putConstraint(SpringLayout.HORIZONTAL_CENTER, btnStart, Spring.constant(0), SpringLayout.HORIZONTAL_CENTER, this);
    }

    private void output(String msg) {
        EventQueue.invokeLater(() -> {
            taOutput.append(msg);
            taOutput.append(System.lineSeparator());
        });
    }

    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        }catch (Exception ignore) {
            return s;
        }
    }

}
