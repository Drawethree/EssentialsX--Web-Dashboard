package dev.drawethree.essdash.listener;

import dev.drawethree.essdash.db.AddonDatabase;
import net.ess3.api.events.UserBalanceUpdateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

/**
 * Records in-game economy activity (pay / sell / eco commands) into the dashboard's
 * transaction ledger. Dashboard-initiated balance changes are logged separately by the
 * controllers (with staff attribution), so only genuine in-game command causes are taken
 * here to avoid double-counting.
 */
public class EconomyLogListener implements Listener {

    private final AddonDatabase db;

    public EconomyLogListener(AddonDatabase db) {
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBalanceUpdate(UserBalanceUpdateEvent e) {
        String source = switch (e.getCause()) {
            case COMMAND_PAY -> "PAY";
            case COMMAND_SELL -> "SELL";
            case COMMAND_ECO -> "ECO";
            default -> null; // API / SPECIAL / UNKNOWN — skip (covers dashboard's own changes)
        };
        if (source == null || e.getPlayer() == null) return;

        BigDecimal oldBal = e.getOldBalance();
        BigDecimal newBal = e.getNewBalance();
        String delta = (oldBal != null && newBal != null) ? newBal.subtract(oldBal).toPlainString() : null;

        db.insertEconomyLog(
                e.getPlayer().getUniqueId(),
                e.getPlayer().getName(),
                delta,
                newBal == null ? null : newBal.toPlainString(),
                source,
                null,
                System.currentTimeMillis());
    }
}
