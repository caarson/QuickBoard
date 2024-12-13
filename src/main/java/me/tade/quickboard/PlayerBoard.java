package me.tade.quickboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.clip.placeholderapi.PlaceholderAPI;
import me.tade.quickboard.events.PlayerReceiveBoardEvent;
import me.tade.quickboard.events.WhenPluginUpdateTextEvent;

public class PlayerBoard {

    private QuickBoard plugin;

    private Scoreboard board;
    private Objective score;
    private Player player;

    private List<Team> teams = new ArrayList<>();
    private HashMap<Team, String> lot = new HashMap<>();
    private List<String> list;
    private List<String> title;
    private List<String> chlist = new ArrayList<>();
    private int updateTitle;
    private int updateText;
    private int titleTask;
    private int textTask;
    private boolean ch = false;
    private ScoreboardInfo info = null;
    private Map<String, Scroller> scrollers;  // Declaration of the scrollers map
    private HashMap<String, String> chanText = new HashMap<>();
    private HashMap<String, Integer> chanTextInt = new HashMap<>();
    private HashMap<String, String> scrollerText = new HashMap<>();
    private List<Integer> tasks = new ArrayList<>();
    private boolean ver13 = false;

    private int index = 15;
    private int titleindex = 0;

    public PlayerBoard(QuickBoard plugin, Player player, ScoreboardInfo info) {
        this.plugin = plugin;
        this.player = player;
    
        // Ensure the info object is not null to avoid NullPointerException
        if (info == null) {
            plugin.getLogger().severe("ScoreboardInfo is null for player " + player.getName());
            return;
        }
    
        // Safely retrieve and initialize all required fields from ScoreboardInfo
        this.list = info.getText() != null ? info.getText() : new ArrayList<>();
        this.title = info.getTitle() != null ? info.getTitle() : new ArrayList<>();
        this.updateTitle = info.getTitleUpdate();
        this.updateText = info.getTextUpdate();
    
        // Initialize changeable text and scrollers if any
        this.chanTextInt = new HashMap<>();
        this.chanText = new HashMap<>();
        for (String key : info.getChangeText().keySet()) {
            List<String> texts = info.getChangeText().get(key);
            if (texts != null && !texts.isEmpty()) {
                chanTextInt.put(key, 0);
                chanText.put(key, texts.get(0));
            }
        }
    
        this.info = info;
        this.scrollers = new HashMap<>();  // Initialize the scrollers map
        initializeScrollers(info);  // Load scrollers based on the configuration
    
        // Create and dispatch the PlayerReceiveBoardEvent
        PlayerReceiveBoardEvent event = new PlayerReceiveBoardEvent(player, list, title, this);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            startSetup(event);
        } else {
            plugin.getLogger().info("Scoreboard event was cancelled for player: " + player.getName());
        }
    }    

    public PlayerBoard(QuickBoard plugin, Player player, List<String> text, List<String> title, int updateTitle, int updateText) {
        this.plugin = plugin;
        this.player = player;
        this.updateTitle = updateTitle;
        this.updateText = updateText;

        this.title = title;
        this.list = text;

        PlayerReceiveBoardEvent event = new PlayerReceiveBoardEvent(getPlayer(), this.list, this.title, this);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            startSetup(event);
        }
    }

    private String updateScrollers(String text) {
        for (Map.Entry<String, Scroller> entry : scrollers.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (text.contains(placeholder)) {
                Scroller scroller = entry.getValue();
                text = text.replace(placeholder, scroller.next());
            }
        }
        return text;
    }    

    private void initializeScrollers(ScoreboardInfo info) {
        Map<String, Scroller> scrollerMap = info.getScrollerText();
        Map<String, Integer> scrollerIntervalMap = info.getScrollerInterval();
    
        for (Map.Entry<String, Scroller> entry : scrollerMap.entrySet()) {
            String key = entry.getKey();
            Scroller scroller = entry.getValue();
            scrollers.put(key, scroller);
            int interval = scrollerIntervalMap.getOrDefault(key, 20); // Default interval if not specified
            plugin.getLogger().info("Scroller initialized for key: " + key + " with interval: " + interval); // Debugging line
            // Schedule scroller updates if needed here
        }
    }     

    public void startSetup(final PlayerReceiveBoardEvent event) {
        plugin.getLogger().info("Starting setup for player scoreboard...");
    
        if (event.getTitle() == null || event.getTitle().isEmpty()) {
            plugin.getLogger().warning("Event title is null or empty. Setting a default title.");
            event.getTitle().add(ChatColor.YELLOW + "Default Title");
        }
    
        if (event.getText() == null || event.getText().isEmpty()) {
            plugin.getLogger().severe("Event text is null or empty. Aborting scoreboard setup.");
            return;
        }
    
        index = Math.max(0, Math.min(index, event.getText().size() - 1));
        buildScoreboard(event);
        setUpText(event.getText());
        updater();
    
        plugin.getLogger().info("Scoreboard successfully set up for player: " + getPlayer().getName());
    }
    
    public void buildScoreboard(PlayerReceiveBoardEvent event) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        score = board.registerNewObjective("score", "dummy", "Player Score");
        score.setDisplaySlot(DisplaySlot.SIDEBAR);
    
        if (event.getTitle() == null || event.getTitle().isEmpty()) {
            plugin.getLogger().warning("Title is null or empty for player " + getPlayer().getName() + ". Adding a default title.");
            event.getTitle().add(ChatColor.YELLOW + "Default Title");
        }
    
        score.setDisplayName(ChatColor.translateAlternateColorCodes('&', event.getTitle().get(0)));
        getPlayer().setScoreboard(board);
    }
    

    // Safe access method for lists
    public <T> T safeGet(List<T> list, int index) {
        if (list != null && index >= 0 && index < list.size()) {
            return list.get(index);
        } else {
            plugin.getLogger().warning("Attempted to access invalid index: " + index + " of list size: " + (list != null ? list.size() : "null"));
            return null; // or handle as appropriate
        }
    }

    public void setUpText(final List<String> text) {
        plugin.getLogger().info("Setting up text for the scoreboard...");
    
        if (text == null || text.isEmpty()) {
            plugin.getLogger().severe("Setup aborted: Text content is missing.");
            return;
        }
    
        int idx = 0;
        for (String line : text) {
            String entry = ChatColor.values()[idx % ChatColor.values().length].toString();
            Team team = board.registerNewTeam("team" + idx);
            team.addEntry(entry);
            score.getScore(entry).setScore(text.size() - idx);
    
            String processed = setHolders(line);
            String[] parts = splitString(processed);
            team.setPrefix(parts[0]);
            if (parts.length > 1) {
                team.setSuffix(parts[1]);
            }
    
            idx++;
        }
        plugin.getLogger().info("Text setup completed.");
    }
     
            
    public void colorize() {
        chlist.clear();
        plugin.getLogger().info("Colorizing entries...");
        if (list == null || list.isEmpty()) {
            plugin.getLogger().severe("Error: Text list is null or empty. Cannot generate chlist.");
            return;
        }
    
        int requiredSize = list.size() + 10; // Add buffer to avoid shortages.
        for (ChatColor color : ChatColor.values()) {
            for (ChatColor color2 : ChatColor.values()) {
                if (chlist.size() < requiredSize) {
                    chlist.add(color.toString() + color2.toString());
                } else {
                    break;
                }
            }
            if (chlist.size() >= requiredSize) break;
        }
    
        if (chlist.size() < requiredSize) {
            plugin.getLogger().severe("Error: Failed to generate enough unique color combinations. Needed: " + requiredSize + ", Generated: " + chlist.size());
        } else {
            plugin.getLogger().info("Generated " + chlist.size() + " unique color combinations successfully.");
        }
    }
    
    public void updateText() {
        Iterator<Team> teams = this.teams.iterator();
        while (teams.hasNext()) {
            Team t = teams.next();
            String s = lot.get(t);
    
            if (info != null) {
                for (String key : info.getChangeText().keySet()) {
                    if (s.contains("{CH_" + key + "}")) {
                        s = s.replace("{CH_" + key + "}", chanText.get(key));
                        plugin.getLogger().info("Change text updated for key: " + key); // Debugging line
                    }
                }
                for (String key : info.getScrollerText().keySet()) {
                    if (s.contains("{SC_" + key + "}")) {
                        Scroller scroller = scrollers.get(key);
                        if (scroller != null) {
                            s = s.replace("{SC_" + key + "}", scroller.next());
                            plugin.getLogger().info("Scroller text updated for key: " + key); // Debugging line
                        }
                    }
                }
            }
    
            s = setHolders(s);
            WhenPluginUpdateTextEvent event = new WhenPluginUpdateTextEvent(getPlayer(), s);
            Bukkit.getPluginManager().callEvent(event);
    
            String[] parts = splitString(event.getText());
            t.setPrefix(parts[0]);
            if (parts.length > 1) {
                t.setSuffix(parts[1]);
            }
        }
    }
    
       
      
    public void setPrefix(Team t, String string) {
        if (string.length() > getMaxSize()) {
            t.setPrefix(maxChars(getMaxSize(), string));
            return;
        }
        t.setPrefix(string);
    }

    public void setSuffix(Team t, String string) {
        if (string.length() > getMaxSize()) {
            t.setSuffix(maxChars(getMaxSize(), string));
            return;
        }
        t.setSuffix(string);
    }

    public String setHolders(String s) {
        // Applying Minecraft color and formatting codes
        s = ChatColor.translateAlternateColorCodes('&', s);
        
        s = s.replace("{PLAYER}", getPlayer().getName())
             .replace("{ONLINE}", Bukkit.getOnlinePlayers().size() + "")
             .replace("{TIME}", getPlayer().getWorld().getTime() + "");
    
        // Apply PlaceholderAPI and MVdWPlaceholderAPI if available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && PlaceholderAPI.containsPlaceholders(s)) {
            s = PlaceholderAPI.setPlaceholders(getPlayer(), s);
        }
        if (plugin.isMVdWPlaceholderAPI()) {
            s = be.maximvdw.placeholderapi.PlaceholderAPI.replacePlaceholders(getPlayer(), s);
        }
    
        return s;
    }
    

    public int getMaxSize() {
        if (ver13)
            return 64;
        return 16;
    }

    public void updateTitle() {
        if (ch) return;
    
        if (title == null || title.isEmpty()) {
            plugin.getLogger().warning("Title list is empty or null. Setting a default title.");
            title = new ArrayList<>();
            title.add(ChatColor.YELLOW + "Default Title");
        }
    
        if (titleindex >= title.size()) {
            titleindex = 0; // Reset to prevent index overflow.
        }
    
        score.setDisplayName(maxChars(ver13 ? 128 : 32, setHolders(title.get(titleindex))));
        titleindex++;
    }
    

    public String maxChars(int characters, String string) {
        if (ChatColor.translateAlternateColorCodes('&', string).length() > characters)
            return string.substring(0, characters);
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    public void remove() {
        stopTasks();
        plugin.getBoards().remove(getPlayer());
        plugin.getAllboards().remove(this);
        getPlayer().setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    protected void stopTasks() {
        Bukkit.getScheduler().cancelTask(titleTask);
        Bukkit.getScheduler().cancelTask(textTask);

        for (int i : tasks)
            Bukkit.getScheduler().cancelTask(i);
    }

    public void updater() {
        titleTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                updateTitle();
            }
        }, 0, updateTitle);

        textTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                updateText();
            }
        }, 0, updateText);

        if (info != null) {
            for (final String s : info.getChangeText().keySet()) {
                int inter = info.getChangeTextInterval().get(s);

                BukkitTask task = new BukkitRunnable() {
                    public void run() {
                        List<String> text = info.getChangeText().get(s);
                        chanTextInt.put(s, chanTextInt.get(s) + 1);

                        if (chanTextInt.get(s) >= text.size()) {
                            chanTextInt.put(s, 0);
                        }
                        int ta = chanTextInt.get(s);
                        chanText.put(s, text.get(ta));
                        updateText();
                    }
                }.runTaskTimer(plugin, 1, inter);

                tasks.add(task.getTaskId());
            }

            for (final String s : info.getScrollerText().keySet()) {
                int inter = info.getScrollerInterval().get(s);

                BukkitTask task = new BukkitRunnable() {
                    public void run() {
                        Scroller text = info.getScrollerText().get(s);
                        String txt = text.text;
                        text.setupText(setHolders(txt), text.width, text.spaceBetween, '&');
                        scrollerText.put(s, text.next());
                        updateText();
                    }
                }.runTaskTimer(plugin, 1, inter);

                tasks.add(task.getTaskId());
            }
        }
    }

    public void createNew(List<String> text, List<String> title, int updateTitle, int updateText) {
        ch = true;
        stopTasks();
        removeAll();
        plugin.getLogger().info("Colorizing scoreboard entries for player: " + getPlayer().getName());
        if (list == null || list.isEmpty()) {
            plugin.getLogger().severe("Error in startSetup: Text list is null or empty for player " + getPlayer().getName() + ". Skipping setup.");
            return;
        }        
        colorize();
        if (text == null || text.isEmpty() || title == null || title.isEmpty()) {
            plugin.getLogger().severe("Error in createNew: Either text list or title list is null/empty for player " + getPlayer().getName() + ". Skipping creation.");
            return;
        }
        this.list = text;
        this.title = title;        
        this.updateText = updateText;
        this.updateTitle = updateTitle;
        titleindex = this.title.size();

        score = board.getObjective("score");

        score.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (this.title.size() <= 0) {
            this.title.add(" ");
        }

        score.setDisplayName(this.title.get(0));

        setUpText(text);

        ch = false;
        updater();
    }

    private void removeAll() {
        chlist.clear();
        score = null;
        titleindex = 0;
        index = 15;
        lot.clear();
        teams.clear();

        for (Team t : board.getTeams()) {
            t.unregister();
        }

        for (String s : board.getEntries()) {
            board.resetScores(s);
        }
    }

    private String getResult(boolean BOLD, boolean ITALIC, boolean MAGIC, boolean STRIKETHROUGH, boolean UNDERLINE, ChatColor color) {
        return ((color != null) && (!color.equals(ChatColor.WHITE)) ? color : "") + "" + (BOLD ? ChatColor.BOLD : "") + (ITALIC ? ChatColor.ITALIC : "") + (MAGIC ? ChatColor.MAGIC : "") + (STRIKETHROUGH ? ChatColor.STRIKETHROUGH : "") + (UNDERLINE ? ChatColor.UNDERLINE : "");
    }

    private String[] splitString(String input) {
        final int MAX_LENGTH = 16; // Standard length for Minecraft scoreboard components
        String lastColorCode = "";
        String lastFormatCode = "";
    
        // This pattern finds Minecraft color codes in the string.
        Pattern pattern = Pattern.compile("(§[0-9a-f])|(§[klmnor])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        int lastMatchStart = 0;
    
        while (matcher.find()) {
            lastMatchStart = matcher.start(); // Update last index of codes found
            String match = matcher.group();
            if (match != null && match.matches("§[klmnor]")) { // Formatting codes like bold, italic, etc.
                lastFormatCode = match;
            } else if (match != null) { // Color codes
                lastColorCode = match;
                lastFormatCode = ""; // Reset format codes after a color change
            }
        }
    
        // Correct the split logic to properly cut the string and maintain format.
        String prefix = input.substring(0, Math.min(input.length(), MAX_LENGTH));
        String suffix = input.length() > MAX_LENGTH ? input.substring(MAX_LENGTH) : "";
    
        // Ensure the prefix does not end in a dangling color code.
        if (prefix.endsWith("§")) {
            prefix = prefix.substring(0, prefix.length() - 1);
            suffix = "§" + suffix;
        }
    
        // Reapply the last color and format code to the beginning of the suffix to maintain formatting.
        suffix = lastColorCode + lastFormatCode + suffix;
    
        return new String[] {prefix, suffix};
    }    
    
    public Scoreboard getBoard() {
        return board;
    }

    public Objective getScore() {
        return score;
    }

    public Player getPlayer() {
        return player;
    }

    public List<String> getList() {
        return list;
    }

    public List<String> getTitle() {
        return title;
    }
}
