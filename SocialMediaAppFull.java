/* SocialMediaAppFull.java
   Corrected full version: CSV storage, avatars, image posts, profile grid,
   hover preview, like/comment/follow, admin export, AURA UI.
   Fixes applied:
   - proper imports (DefaultTableModel, javax.swing.Timer, java.awt.Image)
   - ImageUtils.scale returns Image; ImageIcon constructed from Image
   - Timer ambiguity removed (using javax.swing.Timer import)
   - CSV writing uses "\n" correctly
   - comment IDs use timestamp to avoid counter issues
*/

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public class SocialMediaAppFull extends JFrame {
    // --- Data store (CSV) with image support ---
    interface DataStore {
        boolean init();
        int createUser(String username, String password);
        int getUserId(String username);
        boolean validateLogin(String username, String password);
        boolean addPost(int userId, String content, String imageFilename);
        List<PostItem> fetchTimelineForUser(int userId);
        List<String> allUsernames();
        void follow(int followerId, int followeeId);
        void like(int postId, int userId);
        void comment(int postId, int userId, String text);
        List<PostItem> fetchAllPosts();
        String getAvatarFilename(int userId);
        void setAvatar(int userId, String filename);
    }

    static class CSVStore implements DataStore {
        private final File usersFile = new File("users.csv");
        private final File postsFile = new File("posts.csv");
        private final File followsFile = new File("follows.csv");
        private final File likesFile = new File("likes.csv");
        private final File commentsFile = new File("comments.csv");
        private final File metaFile = new File("meta.csv");
        private final File avatarsDir = new File("avatars");
        private final File postImagesDir = new File("posts_images");

        private int nextUserId = 1;
        private int nextPostId = 1;
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public boolean init() {
            try {
                if (!avatarsDir.exists()) avatarsDir.mkdir();
                if (!postImagesDir.exists()) postImagesDir.mkdir();
                if (!usersFile.exists()) usersFile.createNewFile();
                if (!postsFile.exists()) postsFile.createNewFile();
                if (!followsFile.exists()) followsFile.createNewFile();
                if (!likesFile.exists()) likesFile.createNewFile();
                if (!commentsFile.exists()) commentsFile.createNewFile();
                if (!metaFile.exists()) metaFile.createNewFile();
                List<String> meta = Files.readAllLines(metaFile.toPath());
                for (String line : meta) {
                    String[] a = line.split(",");
                    if (a.length == 2) {
                        if (a[0].equals("nextUserId")) nextUserId = Integer.parseInt(a[1]);
                        if (a[0].equals("nextPostId")) nextPostId = Integer.parseInt(a[1]);
                    }
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void saveMeta() {
            try (FileWriter fw = new FileWriter(metaFile, false)) {
                fw.write("nextUserId," + nextUserId + "\n");
                fw.write("nextPostId," + nextPostId + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized int createUser(String username, String password) {
            if (getUserId(username) != -1) return -1;
            int id = nextUserId++;
            try (FileWriter fw = new FileWriter(usersFile, true)) {
                // id,username,password,avatarFilename
                fw.append(id + "," + escape(username) + "," + escape(password) + "," + "" + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
            saveMeta();
            return id;
        }

        @Override
        public int getUserId(String username) {
            try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 2 && a[1].equals(username)) return Integer.parseInt(a[0]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }

        @Override
        public boolean validateLogin(String username, String password) {
            try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 3 && a[1].equals(username) && a[2].equals(password)) return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public synchronized boolean addPost(int userId, String content, String imageFilename) {
            int id = nextPostId++;
            String now = sdf.format(new Date());
            try (FileWriter fw = new FileWriter(postsFile, true)) {
                // columns: postId,userId,content,createdAt,imageFilename
                fw.append(id + "," + userId + "," + escape(content) + "," + now + "," + escape(imageFilename == null ? "" : imageFilename) + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            saveMeta();
            return true;
        }

        @Override
        public List<PostItem> fetchTimelineForUser(int userId) {
            List<Integer> followees = new ArrayList<>();
            followees.add(userId); // include self
            try (BufferedReader br = new BufferedReader(new FileReader(followsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 2) {
                        int follower = Integer.parseInt(a[0]);
                        int followee = Integer.parseInt(a[1]);
                        if (follower == userId) followees.add(followee);
                    }
                }
            } catch (IOException ignored) {}

            List<PostItem> posts = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(postsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 5) {
                        int pid = Integer.parseInt(a[0]);
                        int uid = Integer.parseInt(a[1]);
                        String content = a[2];
                        String createdAt = a[3];
                        String img = a[4];
                        if (followees.contains(uid)) {
                            int likes = countLikes(pid);
                            int comments = countComments(pid);
                            String uname = lookupUsername(uid);
                            posts.add(new PostItem(pid, uid, uname, content, createdAt, likes, comments, img));
                        }
                    }
                }
            } catch (IOException ignored) {}
            posts.sort((a,b) -> b.createdAt.compareTo(a.createdAt));
            return posts;
        }

        private int countLikes(int postId) {
            int c = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(likesFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 2 && Integer.parseInt(a[0]) == postId) c++;
                }
            } catch (IOException ignored) {}
            return c;
        }

        private int countComments(int postId) {
            int c = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(commentsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 3 && Integer.parseInt(a[1]) == postId) c++;
                }
            } catch (IOException ignored) {}
            return c;
        }

        @Override
        public List<String> allUsernames() {
            List<String> names = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 2) names.add(a[1]);
                }
            } catch (IOException ignored) {}
            Collections.sort(names);
            return names;
        }

        @Override
        public synchronized void follow(int followerId, int followeeId) {
            if (followerId == followeeId) return;
            try (FileWriter fw = new FileWriter(followsFile, true)) {
                fw.append(followerId + "," + followeeId + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized void like(int postId, int userId) {
            try (FileWriter fw = new FileWriter(likesFile, true)) {
                fw.append(postId + "," + userId + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized void comment(int postId, int userId, String text) {
            // use millisecond timestamp for comment id to avoid managing counters
            long cid = System.currentTimeMillis();
            try (FileWriter fw = new FileWriter(commentsFile, true)) {
                fw.append(cid + "," + postId + "," + userId + "," + escape(text) + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public List<PostItem> fetchAllPosts() {
            List<PostItem> list = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(postsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 5) {
                        int pid = Integer.parseInt(a[0]);
                        int uid = Integer.parseInt(a[1]);
                        String content = a[2];
                        String createdAt = a[3];
                        String img = a[4];
                        int likes = countLikes(pid);
                        int comments = countComments(pid);
                        String uname = lookupUsername(uid);
                        list.add(new PostItem(pid, uid, uname, content, createdAt, likes, comments, img));
                    }
                }
            } catch (IOException ignored) {}
            return list;
        }

        @Override
        public String getAvatarFilename(int userId) {
            try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 4 && Integer.parseInt(a[0]) == userId) return a[3];
                }
            } catch (IOException ignored) {}
            return null;
        }

        @Override
        public synchronized void setAvatar(int userId, String filename) {
            try {
                File temp = new File("users_tmp.csv");
                try (BufferedReader br = new BufferedReader(new FileReader(usersFile));
                     FileWriter fw = new FileWriter(temp)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] a = splitCsv(line);
                        if (a.length >= 1 && Integer.parseInt(a[0]) == userId) {
                            String uname = a.length >= 2 ? a[1] : "";
                            String pwd = a.length >= 3 ? a[2] : "";
                            fw.append(a[0] + "," + escape(uname) + "," + escape(pwd) + "," + escape(filename) + "\n");
                        } else fw.append(line + "\n");
                    }
                }
                Files.move(temp.toPath(), usersFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String lookupUsername(int uid) {
            try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = splitCsv(line);
                    if (a.length >= 2 && Integer.parseInt(a[0]) == uid) return a[1];
                }
            } catch (IOException ignored) {}
            return "?";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\n", " ").replace(",", "¬");
        }

        private static String[] splitCsv(String line) {
            // we use '¬' as escaped comma marker in escape()
            return line.split("(?<!¬),");
        }
    }

    // --- UI components ---
    private DataStore store;

    private CardLayout cards = new CardLayout();
    private JPanel root = new JPanel(cards);

    private JTextField loginUser = new JTextField(15);
    private JPasswordField loginPass = new JPasswordField(15);
    private JTextField regUser = new JTextField(15);
    private JPasswordField regPass = new JPasswordField(15);

    private JLabel welcomeLabel = new JLabel();
    private DefaultListModel<PostItem> timelineModel = new DefaultListModel<>();
    private JList<PostItem> timelineList = new JList<>(timelineModel);
    private JTextArea newPostArea = new JTextArea(3, 30);
    private DefaultListModel<String> usersModel = new DefaultListModel<>();
    private JList<String> usersList = new JList<>(usersModel);

    private int currentUserId = -1;
    private String currentUsername = null;
    private boolean dark = false;

    // hover zoom window
    private final JWindow hoverWindow = new JWindow();
    private final JLabel hoverLabel = new JLabel();

    public SocialMediaAppFull() {
        setTitle("SocialMediaApp - AURA GOD MODE");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        store = new CSVStore();
        if (!store.init()) { JOptionPane.showMessageDialog(this, "Storage init failed"); System.exit(1); }

        if (store.allUsernames().isEmpty()) {
            store.createUser("john","123");
            store.createUser("jane","456");
            store.createUser("admin","admin");
            int j = store.getUserId("john"); int ja = store.getUserId("jane");
            store.addPost(j, "Hello from John! #welcome", "");
            store.addPost(ja, "Jane's first post :)", "");
        }

        hoverWindow.getContentPane().add(hoverLabel);
        hoverWindow.setAlwaysOnTop(true);

        root.add(loginPanel(), "login");
        root.add(registerPanel(), "register");
        root.add(homePanel(), "home");
        root.add(adminPanel(), "admin");

        add(root);
        cards.show(root, "login");
        setVisible(true);
    }

    private JPanel loginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.gridx = 0; c.gridy = 0;
        JLabel title = new JLabel("Welcome — Login");
        title.setFont(new Font("Inter", Font.BOLD, 22));
        p.add(title, c);

        c.gridy++; p.add(new JLabel("Username:"), c); c.gridx = 1; p.add(loginUser, c);
        c.gridx = 0; c.gridy++; p.add(new JLabel("Password:"), c); c.gridx = 1; p.add(loginPass, c);

        c.gridx = 0; c.gridy++; JButton loginBtn = new JButton("Login"); p.add(loginBtn, c);
        c.gridx = 1; JButton gotoReg = new JButton("Register"); p.add(gotoReg, c);

        loginBtn.addActionListener(e -> doLogin());
        gotoReg.addActionListener(e -> cards.show(root, "register"));

        return p;
    }

    private JPanel registerPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.gridx = 0; c.gridy = 0;
        JLabel title = new JLabel("Create account");
        title.setFont(new Font("Inter", Font.BOLD, 22));
        p.add(title, c);

        c.gridy++; p.add(new JLabel("Username:"), c); c.gridx = 1; p.add(regUser, c);
        c.gridx = 0; c.gridy++; p.add(new JLabel("Password:"), c); c.gridx = 1; p.add(regPass, c);

        c.gridx = 0; c.gridy++; JButton create = new JButton("Create"); p.add(create, c);
        c.gridx = 1; JButton back = new JButton("Back"); p.add(back, c);

        create.addActionListener(e -> doRegister());
        back.addActionListener(e -> cards.show(root, "login"));
        return p;
    }

    private JPanel homePanel() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        top.add(welcomeLabel);
        JButton avatarBtn = new JButton("Set Avatar"); top.add(avatarBtn);
        JButton logout = new JButton("Logout"); top.add(logout);
        JButton adminBtn = new JButton("Admin"); top.add(adminBtn);
        JButton theme = new JButton("Toggle Theme"); top.add(theme);
        JButton refresh = new JButton("Refresh"); top.add(refresh);

        avatarBtn.addActionListener(e -> chooseAvatar());
        logout.addActionListener(e -> { currentUserId = -1; currentUsername = null; cards.show(root, "login"); });
        adminBtn.addActionListener(e -> cards.show(root, "admin"));
        theme.addActionListener(e -> toggleTheme());
        refresh.addActionListener(e -> refreshHome());

        outer.add(top, BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        center.setDividerLocation(700);

        JPanel timelinePanel = new JPanel(new BorderLayout());
        timelinePanel.setBorder(new EmptyBorder(8,8,8,8));
        JLabel tlabel = new JLabel("Timeline"); tlabel.setFont(new Font("SansSerif", Font.BOLD, 18)); timelinePanel.add(tlabel, BorderLayout.NORTH);
        timelineList.setCellRenderer(new PostRenderer());
        JScrollPane tlScroll = new JScrollPane(timelineList);
        timelinePanel.add(tlScroll, BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout());
        composer.setBorder(new EmptyBorder(6,6,6,6));
        newPostArea.setLineWrap(true); newPostArea.setWrapStyleWord(true);
        composer.add(new JScrollPane(newPostArea), BorderLayout.CENTER);
        JPanel act = new JPanel();
        JButton imgBtn = new JButton("Attach Image"); act.add(imgBtn);
        JButton postBtn = new JButton("Post"); act.add(postBtn);
        JButton likeBtn = new JButton("Like selected"); act.add(likeBtn);
        JButton commentBtn = new JButton("Comment"); act.add(commentBtn);
        composer.add(act, BorderLayout.SOUTH);
        timelinePanel.add(composer, BorderLayout.SOUTH);

        final String[] attachedImage = {null};
        imgBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Images", ImageIO.getReaderFileSuffixes()));
            int r = fc.showOpenDialog(this);
            if (r == JFileChooser.APPROVE_OPTION) {
                File chosen = fc.getSelectedFile();
                try {
                    String destName = System.currentTimeMillis() + "_" + chosen.getName();
                    Files.copy(chosen.toPath(), new File("posts_images", destName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    attachedImage[0] = destName;
                    JOptionPane.showMessageDialog(this, "Image attached: " + destName);
                } catch (IOException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Image attach failed: "+ex.getMessage()); }
            }
        });

        postBtn.addActionListener(e -> {
            store.addPost(currentUserId, newPostArea.getText().trim(), attachedImage[0]);
            attachedImage[0] = null; newPostArea.setText(""); refreshHome();
        });
        likeBtn.addActionListener(e -> doLikeSelected());
        commentBtn.addActionListener(e -> doCommentSelected());

        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(new EmptyBorder(8,8,8,8));
        right.add(new JLabel("Explore Users"), BorderLayout.NORTH);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane usersScroll = new JScrollPane(usersList);
        right.add(usersScroll, BorderLayout.CENTER);
        JPanel userAct = new JPanel();
        JButton followBtn = new JButton("Follow"); userAct.add(followBtn);
        JButton viewProfile = new JButton("View Profile"); userAct.add(viewProfile);
        JTextField search = new JTextField(12); userAct.add(search);
        JButton searchBtn = new JButton("Search"); userAct.add(searchBtn);
        right.add(userAct, BorderLayout.SOUTH);

        followBtn.addActionListener(e -> doFollowSelected());
        viewProfile.addActionListener(e -> doViewProfile());
        searchBtn.addActionListener(e -> doSearch(search.getText()));

        center.setLeftComponent(timelinePanel);
        center.setRightComponent(right);
        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    private JPanel adminPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel();
        top.add(new JLabel("Admin Panel"));
        JButton back = new JButton("Back"); top.add(back);
        JButton export = new JButton("Export Users CSV"); top.add(export);
        p.add(top, BorderLayout.NORTH);

        JTable table = new JTable();
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        back.addActionListener(e -> cards.show(root, "home"));
        export.addActionListener(e -> exportUsersCsv());

        DefaultTableModel model = new DefaultTableModel(new Object[] {"ID","Username"}, 0);
        List<String> names = store.allUsernames();
        int id = 1;
        for (String name : names) model.addRow(new Object[] { id++, name });
        table.setModel(model);
        return p;
    }

    // actions
    private void doLogin() {
        String u = loginUser.getText().trim();
        String p = new String(loginPass.getPassword());
        if (u.isEmpty() || p.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter credentials"); return; }
        if (store.validateLogin(u,p)) {
            currentUserId = store.getUserId(u);
            currentUsername = u;
            welcomeLabel.setText("Welcome @" + currentUsername + " (ID:" + currentUserId + ")");
            refreshHome();
            cards.show(root, "home");
        } else JOptionPane.showMessageDialog(this, "Login failed");
    }

    private void doRegister() {
        String u = regUser.getText().trim();
        String p = new String(regPass.getPassword());
        if (u.isEmpty() || p.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter credentials"); return; }
        int id = store.createUser(u,p);
        if (id != -1) JOptionPane.showMessageDialog(this, "Account created! Please login."); else JOptionPane.showMessageDialog(this, "Registration failed (maybe username taken)");
    }

    private void doLikeSelected() {
        PostItem sel = timelineList.getSelectedValue(); if (sel==null) return;
        // animation: pulse background of selected cell (simple repaint)
        int idx = timelineList.getSelectedIndex();
        if (idx >= 0) {
            final int[] step = {0};
            Timer t = new Timer(30, ev -> {
                step[0]++;
                timelineList.repaint();
                if (step[0] > 10) ((Timer)ev.getSource()).stop();
            });
            t.start();
        }
        store.like(sel.postId, currentUserId);
        refreshHome();
    }

    private void doCommentSelected() {
        PostItem sel = timelineList.getSelectedValue();
        if (sel==null) return;
        String text = JOptionPane.showInputDialog(this, "Enter comment:");
        if (text!=null && !text.trim().isEmpty()) { store.comment(sel.postId, currentUserId, text.trim()); refreshHome(); }
    }

    private void doFollowSelected() {
        String sel = usersList.getSelectedValue();
        if (sel==null) return;
        int id = store.getUserId(sel);
        if (id==-1) return;
        store.follow(currentUserId, id);
        JOptionPane.showMessageDialog(this, "Now following @"+sel);
        refreshHome();
    }

    private void doViewProfile() {
        String sel = usersList.getSelectedValue(); if (sel==null) return; int id = store.getUserId(sel); if (id==-1) return;
        JDialog d = new JDialog(this, "Profile - @" + sel, true);
        d.setSize(800,600); d.setLocationRelativeTo(this);
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String avatar = store.getAvatarFilename(id);
        JLabel avatarLabel = new JLabel();
        if (avatar!=null && !avatar.isEmpty()) {
            try {
                BufferedImage img = ImageIO.read(new File("avatars", avatar));
                Image circle = ImageUtils.createCircle(img, 80);
                avatarLabel.setIcon(new ImageIcon(circle));
            } catch (IOException ignored) { avatarLabel.setText("[No Avatar]"); }
        } else avatarLabel.setText("[No Avatar]");
        avatarLabel.setBorder(new EmptyBorder(6,6,6,6));
        top.add(avatarLabel);
        JLabel nameL = new JLabel("@"+sel + " (ID:"+id+")"); nameL.setFont(new Font("SansSerif", Font.BOLD, 18)); top.add(nameL);
        JButton follow = new JButton("Follow"); top.add(follow);
        follow.addActionListener(e -> { store.follow(currentUserId, id); JOptionPane.showMessageDialog(this, "Followed @"+sel); });
        p.add(top, BorderLayout.NORTH);

        List<PostItem> posts = store.fetchAllPosts();
        JPanel grid = new JPanel(new GridLayout(0,3,8,8));
        for (PostItem pi : posts) if (pi.userId==id) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBorder(new EmptyBorder(4,4,4,4));
            if (pi.imageFilename!=null && !pi.imageFilename.isEmpty()) {
                try {
                    BufferedImage img = ImageIO.read(new File("posts_images", pi.imageFilename));
                    ImageIcon ic = new ImageIcon(ImageUtils.scale(img, 220, 220));
                    JLabel pic = new JLabel(ic); pic.setHorizontalAlignment(SwingConstants.CENTER);
                    pic.addMouseListener(new MouseAdapter() {
                        public void mouseClicked(MouseEvent e) { showImageModal(img, pi); }
                        public void mouseEntered(MouseEvent e) { showHover(img, e); }
                        public void mouseExited(MouseEvent e) { hideHover(); }
                    });
                    card.add(pic, BorderLayout.CENTER);
                } catch (IOException ignored) { card.add(new JLabel(pi.content), BorderLayout.CENTER); }
            } else {
                JTextArea ta = new JTextArea(pi.content); ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setEditable(false); card.add(new JScrollPane(ta), BorderLayout.CENTER);
            }
            card.add(new JLabel("Likes: "+pi.likes+"  Comments: "+pi.comments), BorderLayout.SOUTH);
            grid.add(card);
        }
        JScrollPane scroll = new JScrollPane(grid);
        p.add(scroll, BorderLayout.CENTER);
        d.add(p);
        d.setVisible(true);
    }

    private void showImageModal(BufferedImage img, PostItem pi) {
        JDialog dlg = new JDialog(this, "Post by @"+pi.username, true);
        dlg.setSize(600,600); dlg.setLocationRelativeTo(this);
        JLabel lab = new JLabel(new ImageIcon(ImageUtils.scale(img, 560, 560)));
        JTextArea caption = new JTextArea(pi.content); caption.setEditable(false); caption.setLineWrap(true);
        dlg.add(new JScrollPane(lab), BorderLayout.CENTER);
        dlg.add(new JScrollPane(caption), BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showHover(BufferedImage img, MouseEvent e) {
        try {
            hoverLabel.setIcon(new ImageIcon(ImageUtils.scale(img, 320, 320)));
            Point p = e.getLocationOnScreen();
            hoverWindow.setSize(340,340);
            hoverWindow.setLocation(p.x+15, p.y+15);
            hoverWindow.setVisible(true);
        } catch (Exception ex) { /* ignore */ }
    }
    private void hideHover() { hoverWindow.setVisible(false); }

    private void doSearch(String term) { usersModel.clear(); if (term==null||term.trim().isEmpty()) { store.allUsernames().forEach(usersModel::addElement); return; } for (String u : store.allUsernames()) if (u.toLowerCase().contains(term.toLowerCase())) usersModel.addElement(u); }

    private void refreshHome() {
        welcomeLabel.setText("Welcome @" + currentUsername + " (ID:" + currentUserId + ")");
        usersModel.clear(); store.allUsernames().forEach(usersModel::addElement);
        timelineModel.clear(); store.fetchTimelineForUser(currentUserId).forEach(timelineModel::addElement);
    }

    private void toggleTheme() { dark = !dark; Color bg = dark? new Color(28,28,30): new Color(250,250,250); Color fg = dark? Color.WHITE: Color.DARK_GRAY; getContentPane().setBackground(bg); SwingUtilities.invokeLater(() -> updateComponentTreeUI(this, bg, fg)); }
    private void updateComponentTreeUI(Component comp, Color bg, Color fg) { comp.setBackground(bg); comp.setForeground(fg); if (comp instanceof Container) for (Component c : ((Container) comp).getComponents()) updateComponentTreeUI(c, bg, fg); repaint(); }

    private void exportUsersCsv() {
        try (FileWriter fw = new FileWriter("users_export.csv")) {
            fw.append("id,username,avatar\n");
            try (BufferedReader br = new BufferedReader(new FileReader("users.csv"))) {
                String line; while ((line = br.readLine()) != null) {
                    String[] a = CSVStore.splitCsv(line);
                    if (a.length >= 4) fw.append(a[0] + "," + a[1] + "," + a[3] + "\n");
                }
            } catch (IOException ignored) {}
            JOptionPane.showMessageDialog(this, "Exported to users_export.csv");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage());
        }
    }

    // Avatar chooser for current user
    private void chooseAvatar() {
        if (currentUserId==-1) { JOptionPane.showMessageDialog(this, "Login first"); return; }
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images", ImageIO.getReaderFileSuffixes()));
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File chosen = fc.getSelectedFile();
            try {
                String destName = System.currentTimeMillis() + "_" + chosen.getName();
                File dest = new File("avatars", destName);
                Files.copy(chosen.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                store.setAvatar(currentUserId, destName);
                JOptionPane.showMessageDialog(this, "Avatar set!");
                refreshHome();
            } catch (IOException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Avatar set failed: "+ex.getMessage()); }
        }
    }

    // PostItem & renderer
    static class PostItem {
        int postId, userId; String username, content, createdAt; int likes, comments; String imageFilename;
        PostItem(int postId, int userId, String username, String content, String createdAt, int likes, int comments, String imageFilename) { this.postId = postId; this.userId = userId; this.username = username; this.content = content; this.createdAt = createdAt; this.likes = likes; this.comments = comments; this.imageFilename = imageFilename; }
        public String toString() { return "@"+username+": "+(content.length()>60?content.substring(0,60)+"...":content); }
    }

    static class PostRenderer extends JPanel implements ListCellRenderer<PostItem> {
        private JLabel top = new JLabel();
        private JTextArea body = new JTextArea();
        private JLabel meta = new JLabel();
        private JLabel pic = new JLabel();
        public PostRenderer() {
            setLayout(new BorderLayout());
            top.setFont(new Font("SansSerif", Font.BOLD, 13));
            body.setLineWrap(true); body.setWrapStyleWord(true); body.setEditable(false);
            meta.setFont(new Font("SansSerif", Font.PLAIN, 11));
            add(top, BorderLayout.NORTH); add(body, BorderLayout.CENTER); add(meta, BorderLayout.SOUTH);
            setBorder(new EmptyBorder(8,8,8,8));
        }
        public Component getListCellRendererComponent(JList<? extends PostItem> list, PostItem value, int index, boolean isSelected, boolean cellHasFocus) {
            top.setText("@" + value.username + " (ID:" + value.userId + ")");
            body.setText(value.content);
            meta.setText("At: " + value.createdAt + " | Likes: " + value.likes + " | Comments: " + value.comments);
            if (value.imageFilename != null && !value.imageFilename.isEmpty()) {
                try {
                    BufferedImage img = ImageIO.read(new File("posts_images", value.imageFilename));
                    ImageIcon ic = new ImageIcon(ImageUtils.scale(img, 140, 140));
                    pic.setIcon(ic);
                    if (pic.getParent() == null) add(pic, BorderLayout.EAST);
                } catch (IOException ignored) {
                    pic.setIcon(null);
                }
            } else {
                pic.setIcon(null);
                if (pic.getParent() != null) remove(pic);
            }
            setBackground(isSelected ? new Color(230,230,250) : Color.WHITE);
            return this;
        }
    }

    // --- Image utilities: circular avatars, scaling ---
    static class ImageUtils {
        static Image scale(BufferedImage img, int w, int h) {
            return img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        }
        static Image createCircle(BufferedImage src, int size) {
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape circle = new Ellipse2D.Float(0,0,size,size);
            g2.setClip(circle);
            g2.drawImage(src.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0,0,null);
            g2.dispose();
            return out;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SocialMediaAppFull::new);
    }
}