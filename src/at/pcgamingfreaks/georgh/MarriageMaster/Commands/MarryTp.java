package at.pcgamingfreaks.georgh.MarriageMaster.Commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import at.pcgamingfreaks.georgh.MarriageMaster.MarriageMaster;

public class MarryTp 
{
	private MarriageMaster marriageMaster;
	
	public MarryTp(MarriageMaster marriagemaster)
	{
		marriageMaster = marriagemaster;
	}

	public void TP(Player player)
	{		
		String partner = marriageMaster.DB.GetPartner(player.getName());
		if(partner != null && !partner.isEmpty())
		{
			Player otherPlayer = marriageMaster.getServer().getPlayer(partner);
			if(otherPlayer != null && otherPlayer.isOnline())
			{
				if(player.canSee(otherPlayer))
				{
					if(marriageMaster.config.GetEconomyStatus())
					{
						if(marriageMaster.economy.Teleport(player, marriageMaster.config.GetEconomyTp()))
						{
							DoTP(player, otherPlayer);
						}
					}
					else
					{
						DoTP(player, otherPlayer);
					}
				}
				else
				{
					player.sendMessage(ChatColor.RED + marriageMaster.lang.Get("Ingame.NoTPInVanish"));
				}
			}
			else
			{
				player.sendMessage(ChatColor.RED + marriageMaster.lang.Get("Ingame.PartnerOffline"));
			}
		}
		else
		{
			player.sendMessage(ChatColor.RED + marriageMaster.lang.Get("Ingame.PartnerOffline"));
		}
	}

	private void DoTP(Player player, Player otherPlayer) 
	{
		player.teleport(otherPlayer);
		player.sendMessage(ChatColor.GREEN + marriageMaster.lang.Get("Ingame.TP"));
		otherPlayer.sendMessage(ChatColor.GREEN + marriageMaster.lang.Get("Ingame.TPto"));		
	}
}