import java.util.*;

// Simple User class
class User {
    private String username;
    private String password;
    private List<String> posts;
    private List<String> following;
    
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.posts = new ArrayList<>();
        this.following = new ArrayList<>();
    }
    
    // Getters and setters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<String> getPosts() { return posts; }
    public List<String> getFollowing() { return following; }
    
    public void addPost(String post) {
        if (post.length() <= 280) {
            posts.add(post);
            System.out.println("Post added successfully!");
        } else {
            System.out.println("Post too long! Maximum 280 characters.");
        }
    }
    
    public void followUser(String username) {
        if (!following.contains(username) && !username.equals(this.username)) {
            following.add(username);
            System.out.println("You are now following @" + username);
        } else {
            System.out.println("Cannot follow this user.");
        }
    }
    
    public void displayPosts() {
        System.out.println("\n=== Posts by @" + username + " ===");
        if (posts.isEmpty()) {
            System.out.println("No posts yet.");
        } else {
            for (int i = posts.size() - 1; i >= 0; i--) {
                System.out.println((i + 1) + ". " + posts.get(i));
            }
        }
    }
}

// Main Social Media Application
public class SocialMediaApp {
    private static Map<String, User> users = new HashMap<>();
    private static User currentUser = null;
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        System.out.println("=== Welcome to Mini Social Media ===");
        createSampleUsers();
        
        while (true) {
            if (currentUser == null) {
                showLoginMenu();
            } else {
                showMainMenu();
            }
        }
    }
    
    private static void createSampleUsers() {
        // Create some sample users
        User user1 = new User("john", "123");
        user1.addPost("Hello everyone! This is my first post!");
        user1.addPost("Learning Java is fun! #coding #java");
        
        User user2 = new User("jane", "456");
        user2.addPost("Beautiful day today! ☀️");
        user2.addPost("Working on a new project. Excited!");
        
        User user3 = new User("admin", "admin");
        user3.addPost("Welcome to our social media platform!");
        
        users.put("john", user1);
        users.put("jane", user2);
        users.put("admin", user3);
        
        System.out.println("Sample users created: john/123, jane/456, admin/admin");
    }
    
    private static void showLoginMenu() {
        System.out.println("\n=== Login Menu ===");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline
        
        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                register();
                break;
            case 3:
                System.out.println("Goodbye!");
                System.exit(0);
                break;
            default:
                System.out.println("Invalid choice!");
        }
    }
    
    private static void login() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        User user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            currentUser = user;
            System.out.println("Welcome back, @" + username + "!");
        } else {
            System.out.println("Invalid username or password!");
        }
    }
    
    private static void register() {
        System.out.print("Enter new username: ");
        String username = scanner.nextLine();
        
        if (users.containsKey(username)) {
            System.out.println("Username already exists!");
            return;
        }
        
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        User newUser = new User(username, password);
        users.put(username, newUser);
        currentUser = newUser;
        System.out.println("Account created successfully! Welcome, @" + username + "!");
    }
    
    private static void showMainMenu() {
        System.out.println("\n=== Main Menu - Welcome @" + currentUser.getUsername() + " ===");
        System.out.println("1. Create Post");
        System.out.println("2. View My Posts");
        System.out.println("3. Follow User");
        System.out.println("4. View Timeline");
        System.out.println("5. View All Users");
        System.out.println("6. Logout");
        System.out.print("Choose an option: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline
        
        switch (choice) {
            case 1:
                createPost();
                break;
            case 2:
                currentUser.displayPosts();
                break;
            case 3:
                followUser();
                break;
            case 4:
                viewTimeline();
                break;
            case 5:
                viewAllUsers();
                break;
            case 6:
                System.out.println("Logged out successfully!");
                currentUser = null;
                break;
            default:
                System.out.println("Invalid choice!");
        }
    }
    
    private static void createPost() {
        System.out.print("What's on your mind? (max 280 chars): ");
        String post = scanner.nextLine();
        currentUser.addPost(post);
    }
    
    private static void followUser() {
        System.out.print("Enter username to follow: ");
        String username = scanner.nextLine();
        
        if (users.containsKey(username)) {
            currentUser.followUser(username);
        } else {
            System.out.println("User not found!");
        }
    }
    
    private static void viewTimeline() {
        System.out.println("\n=== Your Timeline ===");
        List<String> following = currentUser.getFollowing();
        
        if (following.isEmpty()) {
            System.out.println("You're not following anyone yet. Follow some users to see their posts!");
            return;
        }
        
        boolean hasPosts = false;
        for (String username : following) {
            User user = users.get(username);
            if (!user.getPosts().isEmpty()) {
                hasPosts = true;
                System.out.println("\n--- Posts by @" + username + " ---");
                for (int i = user.getPosts().size() - 1; i >= 0; i--) {
                    System.out.println("• " + user.getPosts().get(i));
                }
            }
        }
        
        if (!hasPosts) {
            System.out.println("No posts from people you follow yet.");
        }
    }
    
    private static void viewAllUsers() {
        System.out.println("\n=== All Users ===");
        for (String username : users.keySet()) {
            User user = users.get(username);
            System.out.println("@" + username + " (" + user.getPosts().size() + " posts)");
        }
    }
}
