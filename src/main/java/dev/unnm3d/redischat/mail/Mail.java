package dev.unnm3d.redischat.mail;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

@Getter
public class Mail extends AbstractItem {
    private static final int PAGE_LINES = 14;
    private static final int LINE_LENGTH = 19;

    private final double id;
    private final MailGUIManager manager;
    @Setter
    MailCategory category;
    private final String sender;
    private final String receiver;
    private final String title;
    @Setter
    private boolean read = false;
    @Setter
    private String content;

    /**
     * Creates a new mail
     *
     * @param sender   The sender of the mail
     * @param receiver The mail receiver
     * @param title    The title of the mail
     */
    public Mail(@NotNull MailGUIManager manager, @NotNull String sender, @NotNull String receiver, @NotNull String title) {
        this.id = System.currentTimeMillis() + ((int) (Math.random() * 100.0) / 100.0);
        this.manager = manager;
        this.category = receiver.equals("-Public") ? MailCategory.PUBLIC : MailCategory.PRIVATE;
        this.sender = sender;
        this.receiver = receiver;
        this.title = title;
    }

    /**
     * Creates a mail from a serialized string
     *
     * @param id         The mail id
     * @param serialized The serialized mail string
     */
    public Mail(@NotNull MailGUIManager manager, double id, String serialized) {
        this.manager = manager;
        final String[] splitted = serialized.split("§§");
        this.id = id;
        this.category = MailCategory.fromString(splitted[0]);
        this.sender = splitted[1];
        this.receiver = splitted[2];
        this.title = splitted[3];
        this.content = splitted[4];
    }

    public String serialize() {
        return category.toString() + "§§" + sender + "§§" + receiver + "§§" + title + "§§" + content;
    }

    public String serializeWithId() {
        return id + "§§" + category.toString() + "§§" + sender + "§§" + receiver + "§§" + title + "§§" + content;
    }

    @Override
    public ItemProvider getItemProvider() {
        ItemBuilder itemBuilder = new ItemBuilder(Material.PAPER);
        if (content.isEmpty()) {
            return itemBuilder;
        }
        if (read) {
            itemBuilder = new ItemBuilder(manager.getPlugin().guiSettings.mailItem);
        } else {
            itemBuilder = new ItemBuilder(manager.getPlugin().guiSettings.unreadMailItem);
        }

        final ZonedDateTime cal = ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) id),
                TimeZone.getTimeZone(manager.getPlugin().config.mailTimestampZone).toZoneId());
        final String timestampFormat = " <aqua>" + DateTimeFormatter.ofPattern(manager.getPlugin().config.mailTimestampFormat).format(cal);

        return itemBuilder.setDisplayName(new AdventureComponentWrapper(manager.getPlugin().getComponentProvider().parse("<yellow>" + title + timestampFormat)))
                .addLoreLines(new AdventureComponentWrapper(manager.getPlugin().getComponentProvider().parse("<grey>" + content)));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        if (clickType.isLeftClick()) {
            openPreview(player, true);
        } else if (clickType.isRightClick()) {
            manager.openMailOptionsGui(player, this);
        }
    }

    /**
     * Opens the mail preview to the player
     * The most hard part of RedisChat
     *
     * @param player     The player to open the preview to
     * @param readStatus If the mail should be marked as read
     */
    public void openPreview(@NotNull Player player, boolean readStatus) {
        player.closeInventory();

        final List<Component> bookPages = new ArrayList<>();
        final List<String> headerLines = Arrays.asList(manager.getPlugin().messages.mailHeader
                .replace("%sender%", sender)
                .replace("%title%", title)
                .replace("%timestamp%", ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) id),
                                TimeZone.getTimeZone(manager.getPlugin().config.mailTimestampZone).toZoneId())
                        .format(DateTimeFormatter.ofPattern(manager.getPlugin().config.mailTimestampFormat))
                )
                .split("\r?\n"));

        final String[] contentLines = content.split("\r?\n");


        int lineCount = headerLines.size();
        // Fill the rest of the first page with content lines
        int contentIndex = 0;

        // Set the head
        StringBuilder componentText = new StringBuilder(String.join("\n", headerLines) + "\n");
        while (contentIndex < contentLines.length) {

            // Fills the page with content
            while (lineCount < PAGE_LINES && contentIndex < contentLines.length) {
                String line = contentLines[contentIndex++];
                if (line.length() <= LINE_LENGTH) {
                    componentText.append(line).append("\n");
                } else {
                    // If the line is too long, split it and add the first part to the current page
                    componentText.append(line, 0, LINE_LENGTH).append("\n");
                    line = line.substring(LINE_LENGTH);
                    // Put the rest of the line back into the contentLines array for the next page
                    contentLines[--contentIndex] = line;
                }
                lineCount++;
            }
            bookPages.add(manager.getPlugin().getComponentProvider().parse(componentText.toString()));
            componentText = new StringBuilder();
            lineCount = 0;
        }

        //Craft written book with mail contents (easy part)
        final ItemStack writtenBook = new ItemStack(Material.WRITTEN_BOOK);
        writtenBook.setItemMeta(((BookMeta) writtenBook.getItemMeta())
                .toBuilder()
                .author(manager.getPlugin().getComponentProvider().parse(sender))
                .title(manager.getPlugin().getComponentProvider().parse(title))
                .pages(bookPages)
                .build());
        player.openBook(writtenBook);
        manager.readMail(this, player, readStatus);
    }


    public enum MailCategory {
        PRIVATE("p"),
        PUBLIC("P"),
        SENT("s"),
        ;
        private final String keyName;

        /**
         * @param keyName the name of the key
         */
        MailCategory(final String keyName) {
            this.keyName = keyName;
        }

        public static MailCategory fromString(String keyName) {
            return Arrays.stream(MailCategory.values())
                    .filter(category -> category.keyName.equals(keyName))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public String toString() {
            return keyName;
        }
    }
}
