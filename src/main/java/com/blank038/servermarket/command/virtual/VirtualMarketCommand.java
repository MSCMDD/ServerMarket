package com.blank038.servermarket.command.virtual;

import com.blank038.servermarket.ServerMarket;
import com.blank038.servermarket.api.event.PlayerSaleEvent;
import com.blank038.servermarket.data.cache.market.MarketData;
import com.blank038.servermarket.data.cache.sale.SaleCache;
import com.blank038.servermarket.economy.BaseEconomy;
import com.blank038.servermarket.enums.PayType;
import com.blank038.servermarket.filter.FilterBuilder;
import com.blank038.servermarket.filter.impl.KeyFilterImpl;
import com.blank038.servermarket.gui.impl.MarketGui;
import com.blank038.servermarket.i18n.I18n;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * @author Blank038
 */
public class VirtualMarketCommand extends Command {
    private final MarketData marketData;

    public VirtualMarketCommand(MarketData marketData) {
        super(marketData.getShortCommand());
        this.marketData = marketData;
    }

    @Override
    public boolean execute(CommandSender commandSender, String s, String[] strings) {
        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;
            this.performSellCommand(player, strings);
        }
        return true;
    }

    private void performSellCommand(Player player, String[] args) {
        if (this.marketData.getPermission() != null && !this.marketData.getShortCommand().isEmpty()
                && !player.hasPermission(this.marketData.getPermission())) {
            player.sendMessage(I18n.getStrAndHeader("no-permission"));
            return;
        }
        if (args.length == 0) {
            new MarketGui(this.marketData.getMarketKey(), 1, null).openGui(player);
            return;
        }
        if (args.length == 1) {
            player.sendMessage(I18n.getStrAndHeader("price-null"));
            return;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand().clone();
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            player.sendMessage(I18n.getStrAndHeader("hand-air"));
            return;
        }
        boolean denied = false;
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            denied = itemStack.getItemMeta().getLore().stream()
                    .anyMatch((s) -> this.marketData.getShortCommand().contains(s.replace("§", "&")));
        }
        if (this.marketData.getTypeBlackList().contains(itemStack.getType().name()) || denied) {
            player.sendMessage(I18n.getStrAndHeader("deny-item"));
            return;
        }
        int price;
        try {
            price = Integer.parseInt(args[1]);
        } catch (Exception e) {
            player.sendMessage(I18n.getStrAndHeader("wrong-number"));
            return;
        }
        if (price < this.marketData.getMin()) {
            player.sendMessage(I18n.getStrAndHeader("min-price")
                    .replace("%min%", String.valueOf(this.marketData.getMin())));
            return;
        }
        if (price > this.marketData.getMax()) {
            player.sendMessage(I18n.getStrAndHeader("max-price")
                    .replace("%max%", String.valueOf(this.marketData.getMax())));
            return;
        }
        String extraPrice = this.marketData.getExtraMap().entrySet().stream()
                .filter((s) -> new FilterBuilder().addKeyFilter(new KeyFilterImpl(s.getKey())).check(itemStack))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
        if (extraPrice != null && price < Integer.parseInt(extraPrice.split("-")[0])) {
            player.sendMessage(I18n.getStrAndHeader("min-price")
                    .replace("%min%", extraPrice.split("-")[0]));
            return;
        }
        if (extraPrice != null && price > Integer.parseInt(extraPrice.split("-")[1])) {
            player.sendMessage(I18n.getStrAndHeader("max-price")
                    .replace("%max%", extraPrice.split("-")[1]));
            return;
        }
        // 判断玩家上架物品是否上限
        int currentCount = ServerMarket.getStorageHandler().getSaleCountByPlayer(player.getUniqueId(), this.marketData.getMarketKey());
        if (currentCount >= this.marketData.getPermsValueForPlayer(this.marketData.getLimitCountSection(), player)) {
            player.sendMessage(I18n.getStrAndHeader("maximum-sale"));
            return;
        }
        // 判断余额是否足够交上架税
        double tax = this.marketData.getPermsValueForPlayer(this.marketData.getShoutTaxSection(), player);
        if (BaseEconomy.getEconomyBridge(this.marketData.getPayType()).balance(player, this.marketData.getEcoType()) < tax) {
            player.sendMessage(I18n.getStrAndHeader("shout-tax")
                    .replace("%economy%", this.marketData.getEconomyName()));
            return;
        }
        // 扣除费率
        if (tax > 0 && !BaseEconomy.getEconomyBridge(this.marketData.getPayType()).take(player, this.marketData.getEcoType(), tax)) {
            player.sendMessage(I18n.getStrAndHeader("shout-tax")
                    .replace("%economy%", this.marketData.getEconomyName()));
            return;
        }
        // 设置玩家手中物品为空
        player.getInventory().setItemInMainHand(null);
        // 上架物品
        SaleCache saleItem = new SaleCache(UUID.randomUUID().toString(), this.marketData.getMarketKey(), player.getUniqueId().toString(),
                player.getName(), itemStack, PayType.VAULT, this.marketData.getEcoType(), price, System.currentTimeMillis());
        // add sale to storage handler
        ServerMarket.getStorageHandler().addSale(this.marketData.getMarketKey(), saleItem);
        // call PlayerSaleEvent.Sell
        PlayerSaleEvent.Sell event = new PlayerSaleEvent.Sell(player, this.marketData, saleItem);
        Bukkit.getPluginManager().callEvent(event);

        player.sendMessage(I18n.getStrAndHeader("sell"));
        // 判断是否公告
        if (this.marketData.isSaleBroadcast()) {
            String displayName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ?
                    itemStack.getItemMeta().getDisplayName() : itemStack.getType().name();
            Bukkit.getServer().broadcastMessage(I18n.getStrAndHeader("broadcast")
                    .replace("%item%", displayName)
                    .replace("%market_name%", this.marketData.getDisplayName())
                    .replace("%amount%", String.valueOf(itemStack.getAmount()))
                    .replace("%player%", player.getName()));
        }
    }
}