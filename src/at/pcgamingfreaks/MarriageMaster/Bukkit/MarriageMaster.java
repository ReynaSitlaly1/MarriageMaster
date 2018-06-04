/*
 *   Copyright (C) 2016-2018 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks.MarriageMaster.Bukkit;

import at.pcgamingfreaks.Bukkit.Message.Message;
import at.pcgamingfreaks.Bukkit.Updater;
import at.pcgamingfreaks.Bukkit.Utils;
import at.pcgamingfreaks.ConsoleColor;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.DelayableTeleportAction;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarriageMasterReloadEvent;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Marriage;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriageMasterPlugin;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriagePlayer;
import at.pcgamingfreaks.MarriageMaster.Bukkit.BackpackIntegration.BackpackIntegrationManager;
import at.pcgamingfreaks.MarriageMaster.Bukkit.BackpackIntegration.IBackpackIntegration;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Commands.CommandManagerImplementation;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Database.Config;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Database.Database;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Database.Language;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Listener.*;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Placeholder.PlaceholderManager;
import at.pcgamingfreaks.MarriageMaster.Bukkit.SpecialInfoWorker.NoDatabaseWorker;
import at.pcgamingfreaks.StringUtils;
import at.pcgamingfreaks.Updater.UpdateProviders.BukkitUpdateProvider;
import at.pcgamingfreaks.Updater.UpdateProviders.JenkinsUpdateProvider;
import at.pcgamingfreaks.Updater.UpdateProviders.UpdateProvider;
import at.pcgamingfreaks.Version;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

public class MarriageMaster extends JavaPlugin implements MarriageMasterPlugin
{
	private static final int BUKKIT_PROJECT_ID = 74734;
	private static final String JENKINS_URL = "https://ci.pcgamingfreaks.at", JENKINS_JOB = "MarriageMaster V2";
	private static final String RANGE_LIMIT_PERM = "marry.bypass.rangelimit";
	private static Version version = null;
	private static MarriageMaster instance;

	// Global Objects
	private Config config;
	private Language lang;
	private Database database = null;
	private CommandManagerImplementation commandManager = null;
	private IBackpackIntegration backpacksIntegration = null;
	private MarriageManagerImplementation marriageManager = null;
	private PluginChannelCommunicator pluginChannelCommunicator = null;
	private PlaceholderManager placeholderManager = null;

	// Global Settings
	private boolean multiMarriage = false, selfMarriage = false, selfDivorce = false, surnamesEnabled = false, surnamesForced = false;

	// Global Translations
	public String helpPartnerNameVariable, helpPlayerNameVariable;
	public Message messageNotANumber, messageNoPermission, messageNotFromConsole, messageNotMarried, messagePartnerOffline, messagePartnerNotInRange, messageTargetPartnerNotFound,
					messagePlayerNotFound, messagePlayerNotMarried, messagePlayerNotOnline, messagePlayersNotMarried, messageMoved, messageDontMove, messageMarriageNotExact;

	public static MarriageMaster getInstance()
	{
		return instance;
	}

	//region Loading and Unloading the plugin
	@Override
	public void onEnable()
	{
		instance = this;
		version = new Version(this.getDescription().getVersion());
		Utils.warnIfPerWorldPluginsIsInstalled(getLogger()); // Check if PerWorldPlugins is installed and show info

		config = new Config(this);
		lang = new Language(this);
		if(config == null || !config.isLoaded())
		{
			failedToEnablePlugin();
			return;
		}
		if(config.useUpdater()) update(null); // Check for updates
		BackpackIntegrationManager.initIntegration();
		backpacksIntegration = BackpackIntegrationManager.getIntegration();

		if(!load()) // Load Plugin
		{
			failedToEnablePlugin();
			return;
		}
		getLogger().info(StringUtils.getPluginEnabledMessage("Marriage Master"));
	}

	private void failedToEnablePlugin()
	{
		getLogger().info(ConsoleColor.RED + "Failed to enable plugin!" + ConsoleColor.YELLOW + " :( " + ConsoleColor.RESET);
		this.setEnabled(false);
		instance = null;
	}

	@Override
	public void onDisable()
	{
		Updater updater = null;
		if(config != null && config.isLoaded() && database != null)
		{
			if(config.useUpdater()) updater = update(null); // Check for updates
			unload();
		}
		if(placeholderManager != null) placeholderManager.close();
		instance = null;
		if(updater != null) updater.waitForAsyncOperation();
		getLogger().info(StringUtils.getPluginDisabledMessage("Marriage Master"));
	}

	public void reload()
	{
		unload();
		config.reload();
		load();
		getServer().getPluginManager().callEvent(new MarriageMasterReloadEvent());
	}

	private boolean load()
	{
		// Loading base Data
		if(config == null || !config.isLoaded() || lang == null || !lang.load(config.getLanguage(), config.getLanguageUpdateMode()))
		{
			// If we ever reach this code there must be a serious problem, someone probably has put an outdated version of one of our libs into his plugin.
			getLogger().warning(ConsoleColor.RED + "Configuration or language file not loaded correct! Disable plugin." + ConsoleColor.RESET);
			setEnabled(false);
			return false;
		}

		// Loading data
		surnamesEnabled = config.isSurnamesEnabled();
		multiMarriage   = config.isPolygamyAllowed();
		selfMarriage    = config.isSelfMarriageAllowed();
		selfDivorce     = config.isSelfDivorceAllowed();
		surnamesForced  = config.isSurnamesForced() && surnamesEnabled;

		database = Database.getDatabase(this);
		if(database == null)
		{
			getLogger().warning(ConsoleColor.RED + "Failed to connect to database! Please adjust your settings and retry!" + ConsoleColor.RESET);
			new NoDatabaseWorker(this); // Starts the worker that informs everyone with reload permission that the database connection failed.
			return true;
		}
		if(database.useBungee())
		{
			pluginChannelCommunicator = new PluginChannelCommunicator(this);
		}

		// Loading global translations
		helpPlayerNameVariable       = lang.get("Commands.PlayerNameVariable");
		helpPartnerNameVariable      = lang.get("Commands.PartnerNameVariable");
		messageNotFromConsole        = lang.getMessage("NotFromConsole");
		messageNotANumber            = lang.getMessage("Ingame.NaN");
		messageNoPermission          = lang.getMessage("Ingame.NoPermission");
		messageNotMarried            = lang.getMessage("Ingame.NotMarried");
		messagePartnerOffline        = lang.getMessage("Ingame.PartnerOffline");
		messagePartnerNotInRange     = lang.getMessage("Ingame.PartnerNotInRange");
		messagePlayerNotFound        = lang.getMessage("Ingame.PlayerNotFound").replaceAll("\\{PlayerName}", "%s");
		messagePlayerNotMarried      = lang.getMessage("Ingame.PlayerNotMarried").replaceAll("\\{PlayerName}", "%s");
		messagePlayerNotOnline       = lang.getMessage("Ingame.PlayerNotOnline").replaceAll("\\{PlayerName}", "%s");
		messagePlayersNotMarried     = lang.getMessage("Ingame.PlayersNotMarried");
		messageMoved                 = lang.getMessage("Ingame.TP.Moved");
		messageDontMove              = lang.getMessage("Ingame.TP.DontMove").replaceAll("\\{Time}", "%d");
		messageMarriageNotExact      = lang.getMessage("Ingame.MarriageNotExact");
		messageTargetPartnerNotFound = lang.getMessage("Ingame.TargetPartnerNotFound");

		commandManager = new CommandManagerImplementation(this);
		commandManager.init();
		marriageManager = new MarriageManagerImplementation(this);

		// Register Events
		getServer().getPluginManager().registerEvents(new JoinLeaveWorker(this), this);
		getServer().getPluginManager().registerEvents(new OpenRequestCloser(this), this);
		if(config.isBonusXPEnabled())
		{
			if(config.getBonusXpMultiplier() > 1) getServer().getPluginManager().registerEvents(new BonusXP(this), this);
			if(config.isBonusXPSplitOnPickupEnabled()) getServer().getPluginManager().registerEvents(new BonusXpSplitOnPickup(this), this);
		}
		if(config.isHPRegainEnabled()) getServer().getPluginManager().registerEvents(new RegainHealth(this), this);
		if(config.isJoinLeaveInfoEnabled()) getServer().getPluginManager().registerEvents(new JoinLeaveInfo(this), this);
		if(config.isPrefixEnabled() || config.isSuffixEnabled()) getServer().getPluginManager().registerEvents(new ChatPrefixSuffix(this), this);
		if(config.isEconomyEnabled()) getServer().getPluginManager().registerEvents(new EconomyManager(this), this);
		if(config.isCommandExecutorEnabled()) getServer().getPluginManager().registerEvents(new CommandExecutor(this), this);

		placeholderManager = new PlaceholderManager(this);
		return true;
	}

	private void unload()
	{
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
		if(pluginChannelCommunicator != null)
		{
			pluginChannelCommunicator.close();
			pluginChannelCommunicator = null;
		}
		HandlerList.unregisterAll(this);
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
		database.close();
		database = null;
		commandManager.close();
		commandManager = null;
	}
	//endregion

	public Updater update(at.pcgamingfreaks.Updater.Updater.UpdaterResponse output)
	{
		UpdateProvider updateProvider;
		if(config.useUpdaterDevBuilds())
		{
			updateProvider = new JenkinsUpdateProvider(JENKINS_URL, JENKINS_JOB, getLogger());
		}
		else
		{
			updateProvider = new BukkitUpdateProvider(BUKKIT_PROJECT_ID, getLogger());
		}
		Updater updater = new Updater(this, this.getFile(), true, updateProvider);
		updater.update(output);
		return updater;
	}

	public Config getConfiguration()
	{
		return config;
	}

	public Language getLanguage()
	{
		return lang;
	}

	public IBackpackIntegration getBackpacksIntegration()
	{
		return backpacksIntegration;
	}

	public Database getDatabase()
	{
		return database;
	}

	public PluginChannelCommunicator getPluginChannelCommunicator()
	{
		return pluginChannelCommunicator;
	}

	// API Stuff
	public static Version version()
	{
		return version;
	}

	public Version getVersion()
	{
		return version;
	}

	@Override
	public boolean isPolygamyAllowed()
	{
		return multiMarriage;
	}

	@Override
	public boolean isSelfMarriageAllowed()
	{
		return selfMarriage;
	}

	@Override
	public boolean isSelfDivorceAllowed()
	{
		return selfDivorce;
	}

	@Override
	public boolean isSurnamesEnabled()
	{
		return surnamesEnabled;
	}

	@Override
	public boolean isSurnamesForced()
	{
		return surnamesForced;
	}

	@Override
	public @NotNull MarriagePlayer getPlayerData(@NotNull UUID uuid)
	{
		Validate.notNull(uuid, "The uuid of the player must not be null!");
		return database.getPlayer(uuid);
	}

	@Override
	@SuppressWarnings("deprecation")
	public @NotNull MarriagePlayer getPlayerData(@NotNull String name)
	{
		Validate.notNull(name, "The name of the player must not be null!");
		return getPlayerData(getServer().getOfflinePlayer(name));
	}

	@Override
	public @NotNull MarriagePlayer getPlayerData(@NotNull OfflinePlayer player)
	{
		Validate.notNull(player, "The player must not be null!");
		return database.getPlayer(player.getUniqueId());
	}

	@Override
	public @NotNull Collection<? extends Marriage> getMarriages()
	{
		return database.getCache().getLoadedMarriages();
	}

	@Override
	public boolean isInRange(@NotNull Player player1, @NotNull Player player2, double range)
	{
		Validate.notNull(player1, "The first player must not be null!");
		Validate.notNull(player2, "The second player must not be null!");
		return Utils.inRange(player1, player2, range, RANGE_LIMIT_PERM);
	}

	@Override
	public void doDelayableTeleportAction(@NotNull final DelayableTeleportAction action)
	{
		//noinspection ConstantConditions
		if(action == null) return;
		if(action.getDelay() == 0 || action.getPlayer().hasPermission("marry.skiptpdelay"))
		{
			action.run();
		}
		else
		{
			if(action.getPlayer().isOnline())
			{
				final Location p_loc = action.getPlayer().getLocation();
				final double p_hea = action.getPlayer().getHealth();
				messageDontMove.send(action.getPlayer(), action.getDelay()/20L);
				getServer().getScheduler().runTaskLater(this, () -> {
					if(action.getPlayer().isOnline())
					{
						if(p_hea <= action.getPlayer().getHealth() && p_loc.getX() == action.getPlayer().getLocation().getX() && p_loc.getY() == action.getPlayer().getLocation().getY() &&
								p_loc.getZ() == action.getPlayer().getLocation().getZ() && p_loc.getWorld().getName().equalsIgnoreCase(action.getPlayer().getLocation().getWorld().getName()))
						{
							action.run();
						}
						else
						{
							messageMoved.send(action.getPlayer());
						}
					}
				}, action.getDelay());
			}
		}
	}

	@Override
	public @NotNull at.pcgamingfreaks.MarriageMaster.Bukkit.API.CommandManager getCommandManager()
	{
		return commandManager;
	}

	@Override
	public @NotNull at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriageManager getMarriageManager()
	{
		return marriageManager;
	}
}