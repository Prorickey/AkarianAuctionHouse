package net.akarian.auctionhouse.guis;

import lombok.Getter;
import lombok.Setter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.akarian.auctionhouse.AuctionHouse;
import net.akarian.auctionhouse.comparators.*;
import net.akarian.auctionhouse.guis.admin.ListingEditAdminGUI;
import net.akarian.auctionhouse.guis.admin.ShulkerViewAdminGUI;
import net.akarian.auctionhouse.layouts.Layout;
import net.akarian.auctionhouse.listings.Listing;
import net.akarian.auctionhouse.utils.AkarianInventory;
import net.akarian.auctionhouse.utils.Chat;
import net.akarian.auctionhouse.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AuctionHouseGUI implements AkarianInventory {

    @Getter
    private static final HashMap<UUID, AuctionHouseGUI> searchMap = new HashMap<>();
    @Getter
    private final Player player;
    private final Chat chat = AuctionHouse.getInstance().getChat();
    private final Layout layout;
    @Getter
    private int page;
    @Getter
    @Setter
    private SortType sortType;
    @Getter
    private boolean search = false;
    @Getter
    private String searchStr = "";
    @Getter
    @Setter
    private ArrayList<Listing> listings;
    @Getter
    private Inventory inv;
    @Getter
    private boolean adminMode;
    @Getter
    @Setter
    private boolean sortBool;
    private boolean update;

    /**
     * Auction House GUI
     *
     * @param player   Admin Player
     * @param sortType SortType to sort by
     * @param sortBool Greater than or Less than
     * @param page     Auction House page
     */
    public AuctionHouseGUI(Player player, SortType sortType, boolean sortBool, int page) {
        this.player = player;
        this.sortType = sortType;
        this.sortBool = sortBool;
        this.page = page;
        this.layout = AuctionHouse.getInstance().getLayoutManager().getActiveLayout();
        this.listings = new ArrayList<>();
        this.adminMode = AuctionHouse.getInstance().getUserManager().getUser(player).isAdminMode();
        update = true;
    }

    public AuctionHouseGUI adminMode() {
        this.adminMode = true;
        AuctionHouse.getInstance().getUserManager().getUser(player).setAdminMode(true);
        return this;
    }

    /**
     * Search the Auction House
     *
     * @param search Search query
     * @return instance of this class with the search query
     */
    public AuctionHouseGUI search(String search) {
        this.search = !search.equals("");
        this.searchStr = search;
        this.page = 1;
        update = true;
        return this;
    }

    @Override
    public void onGUIClick(Inventory inventory, Player player, int slot, ItemStack itemStack, ClickType clickType) {

        if (slot == layout.getAdminButton()) {
            update = true;
            if (layout.getAdminButton() != -1) {
                if (player.hasPermission("auctionhouse.admin.manage")) {
                    adminMode = !adminMode;
                    if (!adminMode) {
                        inv.setItem(layout.getAdminButton(), ItemBuilder.build(Material.GRAY_DYE, 1, "&cAdmin Mode", Collections.singletonList("&cAdmin mode is disabled.")));
                        AuctionHouse.getInstance().getUserManager().getUser(player).setAdminMode(false);
                    } else {
                        inv.setItem(layout.getAdminButton(), ItemBuilder.build(Material.LIME_DYE, 1, "&cAdmin Mode", Collections.singletonList("&aAdmin mode is enabled.")));
                        AuctionHouse.getInstance().getUserManager().getUser(player).setAdminMode(true);
                    }
                }
            }
            return;
        } else if (slot == layout.getExitButton()) {
            player.closeInventory();
            return;
        } else if (layout.getPreviousPageButtons().contains(slot) && page != 1) {
            page--;
            update = true;
            updateInventory();
            return;
        } else if (slot == layout.getSearchButton()) {
            player.closeInventory();
            searchMap.put(player.getUniqueId(), this);
            chat.sendMessage(player, AuctionHouse.getInstance().getMessages().getGui_ah_sl());
            chat.sendMessage(player, AuctionHouse.getInstance().getMessages().getGui_ah_sr());
            return;
        } else if (slot == layout.getExpiredItemsButton()) {
            update = true;
            player.openInventory(new ExpireReclaimGUI(player, this, 1).getInventory());
            return;
        } else if (slot == layout.getSortButton()) {
            update = true;
            player.openInventory(new SortGUI(this).getInventory());
            return;
        } else if (layout.getNextPageButtons().contains(slot) && (listings.size() > layout.getListingItems().size() * page)) {
            page++;
            update = true;
            updateInventory();
            return;
        }

        //TODO Validate that new page buttons work.

        //Is a Listing

        if (layout.getListingItems().contains(slot)) {
            Listing listing = AuctionHouse.getInstance().getListingManager().get(itemStack);

            if (listing == null) return;

            //Is in admin mode
            if (adminMode) {

                if (clickType.isLeftClick()) {
                    if (clickType.isShiftClick()) {
                        if (itemStack.getType() == Material.SHULKER_BOX) {
                            player.openInventory(new ShulkerViewAdminGUI(listing, this).getInventory());
                            return;
                        }
                    }
                    player.openInventory(new ListingEditAdminGUI(listing, this).getInventory());
                } else if (clickType.isRightClick() && clickType.isShiftClick()) {
                    switch (AuctionHouse.getInstance().getListingManager().safeRemove(player.getUniqueId().toString(), listing)) {
                        case -1:
                            chat.log("Error while trying to safe remove " + chat.formatItem(listing.getItemStack()), AuctionHouse.getInstance().isDebug());
                            break;
                        case 0:
                            chat.log("Tried to safe remove listing " + listing.getId().toString() + " but it is not active.", AuctionHouse.getInstance().isDebug());
                            break;
                        case 1:
                            chat.sendMessage(player, AuctionHouse.getInstance().getMessages().getSafeRemove());
                    }
                }
                return;
            }

            //Is the creator of the listing
            if (listing.getCreator().toString().equals(player.getUniqueId().toString())) {
                if (clickType.isLeftClick()) {
                    player.openInventory(new ListingEditGUI(player, listing, this).getInventory());
                } else if (clickType.isRightClick() && clickType.isShiftClick()) {
                    switch (AuctionHouse.getInstance().getListingManager().expire(listing, false, true, player.getUniqueId().toString())) {
                        case -3:
                            chat.sendMessage(player, "&eThat item is already expired.");
                            break;
                        case -1:
                            chat.log("Error while trying to safe remove " + chat.formatItem(listing.getItemStack()), AuctionHouse.getInstance().isDebug());
                            break;
                        case 0:
                            chat.log("Tried to safe remove listing " + listing.getId().toString() + " but it is not active.", AuctionHouse.getInstance().isDebug());
                            break;
                    }
                }
                return;
            }

            //View shulker or confirm buy
            if (itemStack.getType() == Material.SHULKER_BOX)
                player.openInventory(new ShulkerViewGUI(player, listing, this).getInventory());
            else player.openInventory(new ConfirmBuyGUI(player, listing, this).getInventory());
        }

    }

    @Override
    public Inventory getInventory() {
        inv = Bukkit.createInventory(this, layout.getInventorySize(), chat.format(layout.getInventoryName()));

        //Spacer Items
        for (Integer i : layout.getSpacerItems()) {
            inv.setItem(i, ItemBuilder.build(AuctionHouse.getInstance().getConfigFile().getSpacerItem(), 1, " ", Collections.emptyList()));
        }

        //Admin Button
        if (layout.getAdminButton() != -1) {
            if (player.hasPermission("auctionhouse.admin.manage")) {
                if (!adminMode) {
                    inv.setItem(layout.getAdminButton(), ItemBuilder.build(Material.GRAY_DYE, 1, "&cAdmin Mode", Collections.singletonList("&cAdmin mode is disabled.")));
                } else {
                    inv.setItem(layout.getAdminButton(), ItemBuilder.build(Material.LIME_DYE, 1, "&cAdmin Mode", Collections.singletonList("&aAdmin mode is enabled.")));
                }
            }
        }

        //Close Button
        if (layout.getExitButton() != -1)
            inv.setItem(layout.getExitButton(), ItemBuilder.build(Material.BARRIER, 1, AuctionHouse.getInstance().getMessages().getGui_ah_cn(), AuctionHouse.getInstance().getMessages().getGui_ah_cd()));

        //Listings
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getInstance(), this::updateInventory);

        //Search Item
        if (layout.getSearchButton() != -1)
            inv.setItem(layout.getSearchButton(), ItemBuilder.build(Material.HOPPER, 1, AuctionHouse.getInstance().getMessages().getGui_ah_sn(), AuctionHouse.getInstance().getMessages().getGui_ah_sd()));

        //Reclaim Listings Item
        if (layout.getExpiredItemsButton() != -1)
            inv.setItem(layout.getExpiredItemsButton(), ItemBuilder.build(Material.CHEST, 1, AuctionHouse.getInstance().getMessages().getGui_ah_en(), AuctionHouse.getInstance().getMessages().getGui_ah_ed()));

        //Sort Item
        if (layout.getSortButton() != -1)
            inv.setItem(layout.getSortButton(), ItemBuilder.build(Material.PAPER, 1, AuctionHouse.getInstance().getMessages().getGui_ah_stn(), AuctionHouse.getInstance().getMessages().getGui_ah_std()));

        return inv;
    }

    public boolean search(Listing listing) {
        //Checking if player is searching
        if (this.search) {
            //Check if the search is searching by seller
            if (this.searchStr.startsWith(AuctionHouse.getInstance().getMessages().getGui_ah_st() + ":")) {
                String playerName = searchStr.split(":")[1];
                UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();
                return listing.getCreator().toString().equalsIgnoreCase(playerUUID.toString());
            } else {
                //Returning whether the listing's item contains the search query
                return chat.formatItem(listing.getItemStack()).toLowerCase(Locale.ROOT).contains(searchStr.toLowerCase(Locale.ROOT));
            }
        }
        return true;
    }

    public void updateInventory() {

        if (AuctionHouse.getInstance().getListingManager().getActive().size() == 0) {
            clear();
            updateButtons();
            return;
        }

        int amountToDisplay;
        int amountCanDisplay = layout.getListingItems().size();
        int end = page * amountCanDisplay;
        int displayStart = end - amountCanDisplay;
        ArrayList<Listing> activeListings;
        if (update) {
            activeListings = AuctionHouse.getInstance().getListingManager().getActive();
            if (search) {
                ArrayList<Listing> searchedListings = new ArrayList<>();
                for (Listing listing : activeListings) {
                    if (search(listing)) searchedListings.add(listing);
                }
                activeListings = searchedListings;
            }
            activeListings = sortListings(activeListings);
            listings = activeListings;
        } else {
            activeListings = listings;
        }
        amountToDisplay = Math.min(activeListings.size(), amountCanDisplay);
        ArrayList<ItemStack> displayItems = getDisplays(displayStart, amountToDisplay);
        int tick = 0;

        for (Integer i : layout.getListingItems()) {
            if (displayItems.size() <= tick) {
                inv.setItem(i, null);
            } else
                inv.setItem(i, displayItems.get(tick));
            tick++;
        }

        updateButtons();

    }

    private void updateButtons() {
        //Previous Page
        if (!layout.getPreviousPageButtons().contains(-1)) {
            if (page != 1) {
                ItemStack previous = ItemBuilder.build(Material.NETHER_STAR, 1, AuctionHouse.getInstance().getMessages().getGui_buttons_ppn().replace("%previous%", String.valueOf(page - 1)).replace("%max%", listings.size() % layout.getListingItems().size() == 0 ? String.valueOf(listings.size() / layout.getListingItems().size()) : String.valueOf((listings.size() / layout.getListingItems().size()) + 1)), AuctionHouse.getInstance().getMessages().getGui_buttons_ppd());
                for (Integer i : layout.getPreviousPageButtons()) {
                    inv.setItem(i, previous);
                }
            } else {
                if (layout.isSpacerPageItems()) {
                    for (Integer i : layout.getPreviousPageButtons()) {
                        inv.setItem(i, ItemBuilder.build(AuctionHouse.getInstance().getConfigFile().getSpacerItem(), 1, " ", Collections.emptyList()));
                    }
                }
            }
        }

        //Next Page
        if (!layout.getNextPageButtons().contains(-1)) {
            if (listings.size() > layout.getListingItems().size() * page) {
                ItemStack next = ItemBuilder.build(Material.NETHER_STAR, 1, AuctionHouse.getInstance().getMessages().getGui_buttons_npn().replace("%next%", String.valueOf(page + 1)).replace("%max%", listings.size() % layout.getListingItems().size() == 0 ? String.valueOf(listings.size() / layout.getListingItems().size()) : String.valueOf((listings.size() / layout.getListingItems().size()) + 1)), AuctionHouse.getInstance().getMessages().getGui_buttons_npd());
                for (Integer i : layout.getNextPageButtons()) {
                    inv.setItem(i, next);
                }
            } else {
                if (layout.isSpacerPageItems()) {
                    for (Integer i : layout.getNextPageButtons()) {
                        inv.setItem(i, ItemBuilder.build(AuctionHouse.getInstance().getConfigFile().getSpacerItem(), 1, " ", Collections.emptyList()));
                    }
                }
            }
        }

        //Info Item
        List<String> infoDesc = new ArrayList<>();
        for (String s : AuctionHouse.getInstance().getMessages().getGui_ah_id()) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
                infoDesc.add(PlaceholderAPI.setPlaceholders(player, s.replace("%balance%", chat.formatMoney(AuctionHouse.getInstance().getEcon().getBalance(player))).replace("%items%", String.valueOf(AuctionHouse.getInstance().getListingManager().getActive().size()))));
            else
                infoDesc.add(s.replace("%balance%", chat.formatMoney(AuctionHouse.getInstance().getEcon().getBalance(player))).replace("%items%", String.valueOf(AuctionHouse.getInstance().getListingManager().getActive().size())));
        }
        if (layout.getInfoButton() != -1)
            inv.setItem(layout.getInfoButton(), ItemBuilder.build(Material.BOOK, 1, AuctionHouse.getInstance().getMessages().getGui_ah_in(), infoDesc));

    }

    private void clear() {
        //Set the display items we currently have to null to remove them
        for (Integer i : layout.getListingItems()) {
            inv.setItem(i, null);
        }
    }

    public ArrayList<Listing> sortListings(ArrayList<Listing> l) {
        //Get all active listings and put them in an array
        Listing[] listings = l.toArray(new Listing[0]);

        //Switch between the sort type and set according to the determined direction
        switch (sortType) {
            case OVERALL_PRICE:
                if (!sortBool) Arrays.sort(listings, new PriceComparatorLG());
                else Arrays.sort(listings, new PriceComparatorGL());

                break;
            case TIME_LEFT:
                if (!sortBool) Arrays.sort(listings, new TimeRemainingComparatorLG());
                else Arrays.sort(listings, new TimeRemainingComparatorGL());
                break;
            case AMOUNT:
                if (!sortBool) Arrays.sort(listings, new AmountComparatorLG());
                else Arrays.sort(listings, new AmountComparatorGL());
                break;
            case COST_PER_ITEM:
                if (!sortBool) Arrays.sort(listings, new CostPerComparatorLG());
                else Arrays.sort(listings, new CostPerComparatorGL());
                break;
        }
        return new ArrayList<>(Arrays.asList(listings));
    }

    /**
     * @param start  display number
     * @param amount amount to display
     * @return List of created ItemStacks.
     */
    public ArrayList<ItemStack> getDisplays(int start, int amount) {
        ArrayList<ItemStack> displays = new ArrayList<>();
        List<Listing> activeListings = listings;


        for (int i = start; i < start + amount; i++) {

            if (i >= activeListings.size()) break;
            Listing listing = activeListings.get(i);

            if (adminMode) displays.add(listing.createAdminActiveListing(player));
            else displays.add(listing.createActiveListing(player));

        }

        return displays;
    }

     /*public void updateInventory() {

        List<Listing> searchedListings = new ArrayList<>();
        boolean needsNewListings;

        if (listings.isEmpty()) {
            if (AuctionHouse.getInstance().getListingManager().getActive().isEmpty()) {
                return;
            } else {
                needsNewListings = true;
            }
        } else {
            needsNewListings = false;
            for (Listing listing : listings) {
                if (!listing.isActive()) {
                    needsNewListings = true;
                }
            }
        }

        if (needsNewListings) {
            for (Listing listing : sortedList()) {
                if (!search) {
                    searchedListings.add(listing);
                } else {
                    if (search(listing)) {
                        searchedListings.add(listing);
                    }
                }
            }
            //Settings the stored listings to the listings we want to display
            listings = searchedListings;
        }

        //Set the end of our displayed listings to the page multiplied by the amount of displayed items we have
        int end = page * layout.getListingItems().size();
        //Set the beginning of our displayed listings to the end minus the amount of displayed items we have
        int display = end - layout.getListingItems().size();

        //Loop through the predefined display items from the layout and setting them to listings
        for (Integer i : layout.getListingItems()) {
            //Break from loop if the amount of listings is empty or if we are at the end of our allocated display items
            if (listings.size() == 0 || display >= end || listings.size() == display) {
                break;
            }
            //Get the listing in our desired location
            Listing listing = listings.get(display);
            if (listing.getItemStack() == null) {
                return;
            }
            //Display the active listing
            if (adminMode) inv.setItem(i, listing.createAdminActiveListing(player));
            else inv.setItem(i, listing.createActiveListing(player));
            //Increment our display position
            display++;
        }

        //Previous Page
        if (!layout.getPreviousPageButtons().contains(-1)) {
            if (page != 1) {
                ItemStack previous = ItemBuilder.build(Material.NETHER_STAR, 1, AuctionHouse.getInstance().getMessages().getGui_buttons_ppn().replace("%previous%", String.valueOf(page - 1)).replace("%max%", String.valueOf(listings.size() / layout.getListingItems().size())), AuctionHouse.getInstance().getMessages().getGui_buttons_ppd());
                for (Integer i : layout.getPreviousPageButtons()) {
                    inv.setItem(i, previous);
                }
            } else {
                if (layout.isSpacerPageItems()) {
                    for (Integer i : layout.getPreviousPageButtons()) {
                        inv.setItem(i, ItemBuilder.build(AuctionHouse.getInstance().getConfigFile().getSpacerItem(), 1, " ", Collections.emptyList()));
                    }
                }
            }
        }

        //Next Page
        if (!layout.getNextPageButtons().contains(-1)) {
            if (listings.size() > layout.getListingItems().size() * page) {
                ItemStack next = ItemBuilder.build(Material.NETHER_STAR, 1, AuctionHouse.getInstance().getMessages().getGui_buttons_npn().replace("%next%", String.valueOf(page + 1)).replace("%max%", String.valueOf(listings.size() / layout.getListingItems().size())), AuctionHouse.getInstance().getMessages().getGui_buttons_npd());
                for (Integer i : layout.getNextPageButtons()) {
                    inv.setItem(i, next);
                }
            } else {
                if (layout.isSpacerPageItems()) {
                    for (Integer i : layout.getNextPageButtons()) {
                        inv.setItem(i, ItemBuilder.build(AuctionHouse.getInstance().getConfigFile().getSpacerItem(), 1, " ", Collections.emptyList()));
                    }
                }
            }
        }

        //Info Item
        List<String> infoDesc = new ArrayList<>();
        for (String s : AuctionHouse.getInstance().getMessages().getGui_ah_id()) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
                infoDesc.add(PlaceholderAPI.setPlaceholders(player, s.replace("%balance%", chat.formatMoney(AuctionHouse.getInstance().getEcon().getBalance(player))).replace("%items%", String.valueOf(AuctionHouse.getInstance().getListingManager().getActive().size()))));
            else
                infoDesc.add(s.replace("%balance%", chat.formatMoney(AuctionHouse.getInstance().getEcon().getBalance(player))).replace("%items%", String.valueOf(AuctionHouse.getInstance().getListingManager().getActive().size())));
        }
    } */
}

