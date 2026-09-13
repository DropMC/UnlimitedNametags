package org.alexdev.unlimitednametags.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.google.common.collect.Maps;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.data.TeamData;
import org.alexdev.unlimitednametags.nametags.EntityNameTagManager;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PaperNametagRow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PacketEventsListener extends PacketListenerAbstract {

    /** {@code Entity.DATA_CUSTOM_NAME}. */
    private static final int CUSTOM_NAME_INDEX = 2;
    /** {@code Entity.DATA_CUSTOM_NAME_VISIBLE}. */
    private static final int CUSTOM_NAME_VISIBLE_INDEX = 3;
    /** {@code Display.DATA_TRANSLATION_ID}. */
    private static final int TRANSLATION_INDEX = 11;
    /** Vertical nudge (blocks) for clients on 1.20.1 and lower, which place a mounted display lower than 1.20.2+. */
    private static final float LEGACY_TRANSLATION_OFFSET = 0.45f;

    private final UnlimitedNameTags plugin;
    private final Map<UUID, Map<String, TeamData>> teams;

    public PacketEventsListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.teams = Maps.newConcurrentMap();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @NotNull
    public Map<String, TeamData> getTeams(@NotNull UUID player) {
        return teams.computeIfAbsent(player, p -> Maps.newConcurrentMap());
    }

    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetaData(event);
        } else if (event.getPacketType() == PacketType.Play.Server.CAMERA) {
            handleCamera(event);
        }
    }

    private void handleCamera(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.getNametagManager().isEffectiveShowOwnNametag(player)) {
            return;
        }

        final WrapperPlayServerCamera camera = new WrapperPlayServerCamera(event);
        if (camera.getCameraId() == player.getEntityId()) {
            plugin.getNametagManager().getPacketDisplays(player).forEach(PaperNametagRow::showForOwner);
        } else {
            plugin.getNametagManager().getPacketDisplays(player).forEach(PaperNametagRow::hideForOwner);
        }
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            handleUseEntity(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            handlePlayerInput(event);
        }
    }

    private void handleUseEntity(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        final WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            return;
        }


        switch (packet.getAction()) {
            case START_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), true);
            case STOP_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), false);
            case START_FLYING_WITH_ELYTRA -> plugin.getPlayerListener().logicElytra(player.get());
        }
    }

    private void handlePlayerInput(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);
        final Collection<PaperNametagRow> packetNameTags = plugin.getNametagManager().getPacketDisplays(player);

        boolean updateSneaking = packetNameTags.stream()
                .anyMatch(packetNameTag -> packet.isShift() != packetNameTag.isSneaking());

        if (updateSneaking) {
            plugin.getNametagManager().updateSneaking(player, packet.isShift());
        }
    }

    private void handlePassengers(@NotNull PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            handleEntityPassengers(event, packet);
            return;
        }

        final List<Integer> passengers = collectPassengers(packet.getPassengers());
        final Collection<PaperNametagRow> packetNameTags = plugin.getNametagManager().getPacketDisplays(player.get());
        if (packetNameTags.isEmpty()) {
            plugin.getPacketManager().setPassengers(player.get(), passengers);
            return;
        }

        final List<Integer> displayEntityIds = packetNameTags.stream()
                .map(row -> ((PacketNameTag) row).displayEntityId())
                .toList();
        final Set<Integer> displayEntityIdSet = new HashSet<>(displayEntityIds);
        final List<Integer> vanillaPassengers = passengers.stream()
                .filter(passenger -> !displayEntityIdSet.contains(passenger))
                .toList();
        final List<Integer> updatedPassengers = new ArrayList<>(vanillaPassengers.size() + displayEntityIds.size());
        updatedPassengers.addAll(vanillaPassengers);
        updatedPassengers.addAll(displayEntityIds);

        if (!updatedPassengers.equals(passengers)) {
            packet.setPassengers(updatedPassengers.stream().mapToInt(Integer::intValue).toArray());
            event.markForReEncode(true);
        }

        plugin.getPacketManager().setPassengers(player.get(), vanillaPassengers);
    }

    /**
     * Same re-attach the player path does, for vehicles that are not players. Mounting a renamed horse makes vanilla
     * send a passenger list holding only the rider, which unmounts the nametag display; it then stays frozen at the
     * position it last rode to, including after dismounting.
     */
    private void handleEntityPassengers(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerSetPassengers packet) {
        final EntityNameTagManager manager = plugin.getEntityNametagManager();
        if (manager == null) {
            return;
        }

        final List<Integer> displayEntityIds = manager.displayIdsFor(packet.getEntityId());
        if (displayEntityIds.isEmpty()) {
            return;
        }

        final List<Integer> passengers = collectPassengers(packet.getPassengers());
        final Set<Integer> displayEntityIdSet = new HashSet<>(displayEntityIds);
        final List<Integer> vanillaPassengers = passengers.stream()
                .filter(passenger -> !displayEntityIdSet.contains(passenger))
                .toList();
        final List<Integer> updatedPassengers = new ArrayList<>(vanillaPassengers.size() + displayEntityIds.size());
        updatedPassengers.addAll(vanillaPassengers);
        updatedPassengers.addAll(displayEntityIds);

        if (!updatedPassengers.equals(passengers)) {
            packet.setPassengers(updatedPassengers.stream().mapToInt(Integer::intValue).toArray());
            event.markForReEncode(true);
        }
    }

    @NotNull
    private List<Integer> collectPassengers(int[] passengers) {
        final List<Integer> passengerList = new ArrayList<>(passengers.length);
        for (int passenger : passengers) {
            passengerList.add(passenger);
        }

        return passengerList;
    }

    private boolean preTeamsChecks(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().getBehavior().isDisableDefaultNameTag()) {
            return false;
        }

        return event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_19_3);
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!preTeamsChecks(event)) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (handleForceDisableDefaultNameTag(event, packet)) {
            return;
        }

        final Map<String, TeamData> teams = getTeams(event.getUser().getUUID());
        final String teamName = packet.getTeamName();

        switch (packet.getTeamMode()) {
            case ADD_ENTITIES -> handleAddEntities(event, packet, teams, teamName);
            case REMOVE_ENTITIES -> handleRemoveEntities(packet, teams, teamName);
            case CREATE -> handleCreateTeam(event, packet, teams, teamName);
            case UPDATE -> handleUpdateTeam(event, packet, teams, teamName);
            case REMOVE -> teams.remove(teamName);
        }
    }

    private boolean handleForceDisableDefaultNameTag(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet) {
        if (plugin.getConfigManager().getSettings().getBehavior().isForceDisableDefaultNameTag()) {
            if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
                packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
                event.markForReEncode(true);
            }
            return true;
        }
        return false;
    }

    private void handleAddEntities(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet,
                                   @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        if (teamDataOpt.isEmpty()) {
            return;
        }

        final TeamData teamData = teamDataOpt.get();
        teamData.getMembers().addAll(packet.getPlayers());

        if (!teamData.isChangedVisibility() && packet.getPlayers().stream().anyMatch(this::existsPlayer)) {
            teamData.setChangedVisibility(true);
            final WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = teamData.getTeamInfo();
            teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
            if (teamData.getTeamInfo() != null) {
                event.getUser().sendPacket(new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE, teamData.getTeamInfo(), teamData.getMembers()));
            }
        }
    }

    private void handleRemoveEntities(@NotNull WrapperPlayServerTeams packet, @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        teamDataOpt.ifPresent(teamData -> teamData.getMembers().removeAll(packet.getPlayers()));
    }

    private void handleCreateTeam(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet,
                                  @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        if (teams.containsKey(teamName)) {
            return;
        }

        packet.getTeamInfo().ifPresent(teamInfo -> {
            final TeamData teamData = new TeamData(teamName, teamInfo, Set.copyOf(packet.getPlayers()));
            teams.put(teamName, teamData);

            if (teamData.getMembers().stream().anyMatch(this::existsPlayer)) {
                teamData.setChangedVisibility(true);
                // Ensure teamInfo is not null before setting visibility
                if (teamData.getTeamInfo() != null) {
                    teamData.getTeamInfo().setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }
            }
        });
    }

    private void handleUpdateTeam(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet, @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        if (teamDataOpt.isEmpty()) {
            return;
        }

        final TeamData teamData = teamDataOpt.get();
        packet.getTeamInfo().ifPresent(teamInfoFromPacket -> {
            if (teamData.isChangedVisibility() && teamInfoFromPacket.getTagVisibility() != WrapperPlayServerTeams.NameTagVisibility.NEVER) {
                teamInfoFromPacket.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                event.markForReEncode(true);
            }
            teamData.setTeamInfo(teamInfoFromPacket);
        });
    }

    public void removePlayerData(@NotNull Player player) {
        teams.remove(player.getUniqueId());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleMetaData(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }

        if (stripManagedEntityName(event)) {
            return;
        }

        final float bedrockOffset = plugin.getConfigManager().getSettings().getBedrock().getNametagYOffset();
        final boolean bedrock = bedrockOffset != 0f && plugin.isBedrockPlayer(viewer);
        final boolean legacyClient = event.getUser().getClientVersion().isOlderThan(ClientVersion.V_1_20_2);
        if (!bedrock && !legacyClient) {
            return;
        }

        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        final Optional<PaperNametagRow> textDisplay = plugin.getNametagManager().getPacketDisplayByEntityId(packet.getEntityId());
        if (textDisplay.isEmpty()) {
            return;
        }

        final float adjustment = translationYAdjustment(textDisplay.get(), bedrock, bedrockOffset, legacyClient);
        if (adjustment == 0f) {
            return;
        }

        for (final EntityData eData : packet.getEntityMetadata()) {
            if (eData.getIndex() == TRANSLATION_INDEX) {
                final Vector3f old = (Vector3f) eData.getValue();
                final Vector3f newV = new Vector3f(old.getX(), old.getY() + adjustment, old.getZ());
                eData.setValue(newV);
                event.markForReEncode(true);
                return;
            }
        }
    }

    /**
     * Vertical correction (blocks) for the translation this viewer is about to receive.
     * <p>
     * Bedrock viewers get one because Geyser turns a text display into the floating name of an invisible armor
     * stand, and places that armor stand too high while the display rides a player. Only TEXT rows are touched:
     * the seat Geyser gives a ridden display reads the translation of a text display and of nothing else, so
     * moving an ITEM or BLOCK row would change nothing on Bedrock.
     * Clients on 1.20.1 and lower keep the nudge this listener has always applied to them.
     */
    private float translationYAdjustment(@NotNull PaperNametagRow display, boolean bedrock, float bedrockOffset,
                                         boolean legacyClient) {
        if (bedrock && ((PacketNameTag) display).getCreatedDisplayType() == NametagDisplayType.TEXT) {
            return bedrockOffset;
        }
        return legacyClient ? LEGACY_TRANSLATION_OFFSET : 0f;
    }

    /**
     * Drops the vanilla custom name from entities the plugin already draws a display for, so the two do not stack.
     * Index 2 is {@code Entity.DATA_CUSTOM_NAME} and index 3 is {@code Entity.DATA_CUSTOM_NAME_VISIBLE}; both sit on
     * the base entity class, so the indices hold for every entity type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean stripManagedEntityName(@NotNull PacketSendEvent event) {
        final EntityNameTagManager manager = plugin.getEntityNametagManager();
        if (manager == null || !manager.shouldHideVanillaName()) {
            return false;
        }

        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        if (!manager.isManaged(packet.getEntityId())) {
            return false;
        }

        final List metadata = new ArrayList(packet.getEntityMetadata());
        final boolean removed = metadata.removeIf(data ->
                ((EntityData) data).getIndex() == CUSTOM_NAME_INDEX
                        || ((EntityData) data).getIndex() == CUSTOM_NAME_VISIBLE_INDEX);
        if (!removed) {
            return false;
        }

        packet.setEntityMetadata(metadata);
        event.markForReEncode(true);
        return true;
    }

    public boolean existsPlayer(@NotNull String name) {
        return plugin.getPlayerListener().getPlayerNameId().containsKey(name);
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
