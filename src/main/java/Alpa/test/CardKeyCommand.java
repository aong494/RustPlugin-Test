package Alpa.test;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CardKeyCommand implements CommandExecutor {
    private final CardKeyManager manager;

    public CardKeyCommand(CardKeyManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }

        if (args.length == 0) return false;

        // 타겟 블록 가져오기 (5칸 거리)
        Block targetBlock = player.getTargetBlockExact(5);

        if (args[0].equalsIgnoreCase("설정")) {
            if (targetBlock == null) {
                player.sendMessage(ChatColor.RED + "설정할 카드키 리더기 블록을 바라봐주세요.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "사용법: /카드키 설정 [레벨(1-3)]");
                return true;
            }

            try {
                int level = Integer.parseInt(args[1]);
                if (level < 1 || level > 3) {
                    player.sendMessage(ChatColor.RED + "레벨은 1, 2, 3 중 하나여야 합니다.");
                    return true;
                }

                manager.setReaderLevel(targetBlock.getLocation(), level);
                player.sendMessage(ChatColor.GREEN + "이 블록이 레벨 " + level + " 리더기로 설정되었습니다.");

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "숫자를 입력해주세요.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("연결")) {
            if (targetBlock == null) {
                player.sendMessage(ChatColor.RED + "연결을 시작할 리더기 블록을 바라봐주세요.");
                return true;
            }

            // 리더기로 등록된 블록인지 확인
            if (manager.getReaderLevel(targetBlock.getLocation()) == null) {
                player.sendMessage(ChatColor.RED + "이 블록은 아직 리더기로 설정되지 않았습니다. '/카드키 설정 [레벨]'을 먼저 해주세요.");
                return true;
            }

            // 연결 대기 상태로 전환
            manager.pendingLinks.put(player.getUniqueId(), targetBlock.getLocation());
            player.sendMessage(ChatColor.YELLOW + "리더기가 선택되었습니다! 이제 연결할 '문(Door)'을 우클릭해주세요.");
            return true;
        }

        // 데이터 리로드 (선택 사항)
        if (args[0].equalsIgnoreCase("reload")) {
            manager.loadConfig();
            player.sendMessage(ChatColor.GREEN + "설정을 다시 불러왔습니다.");
            return true;
        }

        return false;
    }
}