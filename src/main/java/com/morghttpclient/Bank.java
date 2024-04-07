package com.morghttpclient;

import com.morghttpclient.pojos.BankItem;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class Bank {
    private static final Logger logger = LoggerFactory.getLogger(Bank.class);

    private final Client client;

    @Inject
    private ItemManager itemManager;

    private ItemContainer lastBankContainer;
    private boolean lastBankOpenStatus;
    private boolean bankOpenCached;

    private List<BankItem> itemsCache = new ArrayList<>();

    public Bank(Client client) {
        this.client = client;
    }

    public List<BankItem> getItems(){
        return itemsCache;
    }

    public void handleBankWindow(){
        Widget con = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if(con != null) {
            lastBankOpenStatus = true;
            lastBankContainer = client.getItemContainer(InventoryID.BANK);
            if(!bankOpenCached){
                bankOpenCached = true;
                logger.debug("Detected opening of bank window, caching items");
                this.getBankItems();
            }
        }else if(lastBankOpenStatus){
            bankOpenCached = lastBankOpenStatus = false;

            logger.debug("Detected closing of bank window.");
            this.getBankItems();
        }
    }

    private void getBankItems(){
        //Ensure bankContainer is valid
        if(lastBankContainer == null){
            return;
        }

        Item[] bankItems = lastBankContainer.getItems();
        List<BankItem> items = new ArrayList<>();

        for(Item bankItem : bankItems){
            int id = bankItem.getId();

            // Skip invalid item ids
            if (id <= -1){
                continue;
            }

            int quantity = bankItem.getQuantity();

            BankItem bankItemToAdd = new BankItem(id, quantity);
            items.add(bankItemToAdd);
        }
        for(BankItem bankItem : items){
            logger.debug(bankItem.getId() + "," + bankItem.getQuantity());
        }
        logger.debug("Caching items");
        itemsCache = items;
    }
}
