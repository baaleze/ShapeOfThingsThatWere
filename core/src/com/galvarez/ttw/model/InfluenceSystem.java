package com.galvarez.ttw.model;

import static java.lang.Integer.compare;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySystem;
import com.artemis.annotations.Wire;
import com.artemis.utils.ImmutableBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.galvarez.ttw.EntityFactory;
import com.galvarez.ttw.model.DiplomaticSystem.State;
import com.galvarez.ttw.model.components.AIControlled;
import com.galvarez.ttw.model.components.Army;
import com.galvarez.ttw.model.components.Capital;
import com.galvarez.ttw.model.components.Diplomacy;
import com.galvarez.ttw.model.components.InfluenceSource;
import com.galvarez.ttw.model.components.InfluenceSource.Modifiers;
import com.galvarez.ttw.model.components.Policies;
import com.galvarez.ttw.model.data.Culture;
import com.galvarez.ttw.model.data.Empire;
import com.galvarez.ttw.model.data.SessionSettings;
import com.galvarez.ttw.model.map.GameMap;
import com.galvarez.ttw.model.map.Influence;
import com.galvarez.ttw.model.map.MapPosition;
import com.galvarez.ttw.model.map.MapTools;
import com.galvarez.ttw.model.map.MapTools.Border;
import com.galvarez.ttw.rendering.NotificationsSystem;
import com.galvarez.ttw.rendering.NotificationsSystem.Type;
import com.galvarez.ttw.rendering.components.Name;
import com.galvarez.ttw.screens.overworld.OverworldScreen;

/**
 * This classes computes the influence from the different sources (i.e. cities)
 * and update it every turn.
 * <p>
 * The game is centered on the influence idea. It starts from a source with a
 * certain power and flow on the neighboring map tiles. The throughput depends
 * on the source power, distance to the source and terrain cost/difficulty. When
 * multiple sources influence the same tile, it belongs to the source with the
 * highest influence score. For ease of understanding, influence is expressed as
 * percentages.
 * <p>
 * Ideally the influence progression should be:
 * <ul>
 * <li>at a constant pace and continuous from a turn to the next (for instance
 * one tile per turn)
 * <li>with decreasing values from the civilized center to the wild or disputed
 * border
 * <li>on a small scale so that close influence sources can fight for tiles
 * <p>
 * The model that works the best seems to be a linear interpolation of the
 * target influence. The target influence is computed as a waterfall algorithm
 * from the source.
 * 
 * @author Guillaume Alvarez
 */
@Wire
public final class InfluenceSystem extends EntitySystem {

  private static final Logger log = LoggerFactory.getLogger(InfluenceSystem.class);

  public static final int INITIAL_POWER = 100;

  private ComponentMapper<Empire> empires;

  private ComponentMapper<Policies> policies;

  private ComponentMapper<InfluenceSource> sources;

  private ComponentMapper<Diplomacy> relations;

  private ComponentMapper<Army> armies;

  private ComponentMapper<MapPosition> positions;

  private ComponentMapper<Capital> capitals;

  private ComponentMapper<AIControlled> ai;

  private NotificationsSystem notifications;

  private final GameMap map;

  private final SessionSettings settings;

  private final Random rand = new Random();

  private final OverworldScreen screen;

  @SuppressWarnings("unchecked")
  public InfluenceSystem(GameMap gameMap, SessionSettings settings, OverworldScreen screen) {
    super(Aspect.getAspectForAll(InfluenceSource.class));
    this.map = gameMap;
    this.settings = settings;
    this.screen = screen;
  }

  @Override
  protected boolean checkProcessing() {
    return true;
  }

  @Override
  protected void inserted(Entity e) {
    InfluenceSource source = sources.get(e);
    source.power = INITIAL_POWER;
    if (source.power > 0) {
      MapPosition pos = positions.get(e);

      // first influence own tile
      Influence tile = map.getInfluenceAt(pos);
      tile.setInfluence(e, tile.getMaxInfluence());
      source.influencedTiles.add(pos);
    }
  }

  @Override
  protected void processEntities(ImmutableBag<Entity> cities) {
    // must apply each step to all sources to have a consistent behavior

    // first apply delta from previous turn and display it
    for (int x = 0; x < map.height; x++) {
      for (int y = 0; y < map.width; y++) {
        updateTileInfluence(x, y);
      }
    }

    // and update source power
    for (Entity city : cities) {
      updateInfluencedTiles(city);
      accumulatePower(city);
    }

    // then compute the delta for every entity and tile
    for (Entity city : cities) {
      InfluenceSource source = sources.get(city);
      Entity empire = source.empire;

      IntIntMap armyInfluenceOn = new IntIntMap();
      Diplomacy diplo = relations.get(empire);
      int armyPower = armies.get(empire).militaryPower;
      for (Entity enemy : diplo.getEmpires(State.WAR))
        armyInfluenceOn.put(enemy.getId(), armyPower - armies.get(enemy).militaryPower);

      if (!checkInfluencedByOther(source, city))
        addDistanceDelta(source, city, armyInfluenceOn);
    }

    for (Entity city : cities) {
      InfluenceSource source = sources.get(city);
      checkRevolt(city, source, source.empire);
    }
  }

  /**
   * When a city tile main influence belongs to an other empire, we switch the
   * empire city: it was conquered.
   */
  private boolean checkInfluencedByOther(InfluenceSource source, Entity e) {
    Influence tile = map.getInfluenceAt(positions.get(e));
    if (tile.isMainInfluencer(e) || !tile.hasMainInfluence())
      return false;

    InfluenceSource influencer = sources.get(tile.getMainInfluenceSource(world));
    if (source.empire == influencer.empire)
      return false;

    log.info("{} conquered by {}, will no longer influence tiles for {}", e.getComponent(Name.class).name,
        influencer.empire.getComponent(Name.class), source.empire.getComponent(Name.class));
    if (source.empire.getComponent(Empire.class).culture == influencer.empire.getComponent(Empire.class).culture)
      source.power = max(1, source.power / 2);
    else
      source.power = 1;
    source.empire = influencer.empire;
    return true;
  }

  private void updateTileInfluence(int x, int y) {
    Influence tile = map.getInfluenceAt(x, y);
    for (IntIntMap.Entry e : tile.getDelta()) {
      if (e.value != 0) {
        InfluenceSource source = sources.get(world.getEntity(e.key));
        EntityFactory.createFadingTileLabel(world, e.value > 0 ? "+" + e.value : Integer.toString(e.value),
            empires.get(source.empire).color, x, y);
      }
    }
    tile.applyDelta();
    // do not forget to update the main source
    Entity main = tile.getMainInfluenceSource(world);
    if (main != null)
      sources.get(main).influencedTiles.add(map.getPositionAt(x, y));
  }

  /**
   * Spread source influence around the source. For each tile already influenced
   * or next to one we compute the minimal distance to the source. Then from the
   * distance we compute the target influence level. Then apply the delta.
   */
  private void addDistanceDelta(InfluenceSource source, Entity e, IntIntMap armyInfluenceOn) {
    Map<MapPosition, Integer> distances = getDistances(e, positions.get(e));

    for (Entry<MapPosition, Integer> entry : distances.entrySet()) {
      Influence tile = map.getInfluenceAt(entry.getKey());
      int target = canInfluence(e, entry.getKey()) ? target = max(0, source.power - max(0, entry.getValue().intValue()))
          // start losing influence when no neighboring tile
          : 0;
      // do not forget the military from war
      if (tile.hasMainInfluence())
        target += armyInfluenceOn.get(sources.get(tile.getMainInfluenceSource(world)).empire.getId(), 0);

      int current = tile.getInfluence(e);
      if (target > current)
        tile.addInfluenceDelta(e, max(1, (target - current) / 10));
      else if (target < current)
        tile.addInfluenceDelta(e, -max(1, (current - target) / 10));
      // do nothing if same obviously
    }
  }

  /**
   * Remove from {@link InfluenceSource#influencedTiles} all the tiles the
   * entity is no longer the main influencer on.
   */
  private void updateInfluencedTiles(Entity e) {
    InfluenceSource source = sources.get(e);

    Predicate<MapPosition> isNotMain = p -> !map.getInfluenceAt(p).isMainInfluencer(e);
    source.influencedTiles.removeIf(isNotMain);
  }

  /**
   * Update the source power. Each turn we add the number of influenced tiles to
   * the advancement. When it reaches 10*(power+1) then power is increased by 1
   * and advancement is reset. The new power is also added to the influence on
   * source tile.
   * <p>
   * Power is lost when the current influenced size is 10 times below the
   * current power.
   */
  private void accumulatePower(Entity e) {
    InfluenceSource source = sources.get(e);

    int increase = source.growth + source.influencedTiles.size() - source.power / 10;
    if (increase > 0) {
      Diplomacy diplomacy = relations.get(source.empire);
      List<Entity> tributes = diplomacy.getEmpires(State.TRIBUTE);
      int remains = increase;
      for (Entity other : tributes) {
        int tribute = min(remains, increase / tributes.size());
        sources.get(capitals.get(other).capital).powerAdvancement += tribute;
        remains -= tribute;
      }
      increase = remains;
    }

    source.powerAdvancement += increase;
    if (source.powerAdvancement < 0) {
      source.power--;
      source.powerAdvancement = getRequiredPowerAdvancement(source);
    } else if (source.powerAdvancement >= getRequiredPowerAdvancement(source)) {
      source.powerAdvancement = 0;
      source.power++;
    }
  }

  private void checkRevolt(Entity city, InfluenceSource source, Entity empire) {
    Policies empirePolicies = policies.get(empire);
    int stability = empirePolicies.stability;
    if (stability < 100 && rand.nextInt(100) >= stability) {
      // revolt happens, select the tile!
      Optional<Influence> tile = source.influencedTiles
          .stream()
          .filter(p -> map.getTerrainAt(p).canStart() && !map.isOnMapBorder(p)//
              && map.getEntityAt(p.x, p.y) == null && isAtCityBorder(city, p))
          .map(p -> map.getInfluenceAt(p))
          .min(
              (i1, i2) -> compare(i1.getInfluence(city) + i1.getDelta(city) * 2,
                  i2.getInfluence(city) + i2.getDelta(city) * 2));
      if (tile.isPresent()) {
        Influence inf = tile.get();
        Entity mainInfluence = world.getEntity(inf.getMainInfluenceSource());
        Culture culture = empires.get(sources.get(mainInfluence).empire).culture;
        Empire data = new Empire(settings.guessColor(), culture, true);
        settings.empires.add(data);
        Entity revoltCity = EntityFactory
            .createCity(world, inf.position.x, inf.position.y, culture.newCityName(), data)//
            .edit().add(new AIControlled()).getEntity();
        Entity revoltEmpire = EntityFactory.createEmpire(world, revoltCity, data)//
            .edit().add(new AIControlled()).getEntity();
        inf.setInfluence(revoltCity,
            inf.getInfluence(mainInfluence) + inf.getDelta(mainInfluence) + inf.terrain.moveCost() + 1);
        inf.addInfluenceDelta(revoltCity, 100 - stability);
        log.info("Created revolting city {} for empire {}", revoltCity.getComponent(Name.class),
            revoltEmpire.getComponent(Name.class));
        if (!ai.has(city))
          notifications.addNotification(() -> screen.select(revoltCity, false), null, Type.REVOLT,
              "City of %s revolted from %s!", revoltCity.getComponent(Name.class), city.getComponent(Name.class));
        // reset stability to avoid popping revolts in loop
        empirePolicies.stability = 100;
      } else
        log.warn("Found no tile to revolt for {} with stability at {}%", city.getComponent(Name.class), stability);
    }
  }

  private boolean isAtCityBorder(Entity city, MapPosition pos) {
    for (Border b : Border.values()) {
      MapPosition neighbor = MapTools.getNeighbor(b, pos.x, pos.y);
      // don't accept tiles on map border
      if (!map.isOnMap(neighbor))
        return false;

      // accept if different overlord
      if (!map.getInfluenceAt(neighbor).isMainInfluencer(city))
        return true;
    }
    return false;
  }

  public int getRequiredPowerAdvancement(InfluenceSource source) {
    // TODO the base power should depend on the empire
    return 10 * (source.power + 1);
  }

  /**
   * Can only influence a tile if it belongs to the source or one of its
   * neighbor does. Cannot influence if we have a treaty.
   */
  public boolean canInfluence(Entity source, MapPosition pos) {
    // that tile should never be null: there is a flag on it
    Influence influence = map.getInfluenceAt(pos);
    if (influence.isMainInfluencer(source))
      return true;

    // cannot influence on tiles from empires we have a treaty with
    if (influence.hasMainInfluence()) {
      Diplomacy treaties = relations.get(sources.get(source).empire);
      if (treaties.getRelationWith(sources.get(influence.getMainInfluenceSource(world)).empire) == State.TREATY)
        return false;
    }

    // need s neighbor we already have influence on
    for (Border b : Border.values()) {
      MapPosition neighbor = MapTools.getNeighbor(b, pos.x, pos.y);
      Influence tile = map.getInfluenceAt(neighbor);
      if (tile != null && tile.isMainInfluencer(source))
        return true;
    }
    return false;
  }

  public Map<MapPosition, Integer> getDistances(Entity source, MapPosition pos) {
    Map<MapPosition, Integer> distances = new HashMap<>();
    distances.put(pos, 0);
    collectDistances(source, 0, pos, distances, sources.get(source).modifiers);
    return distances;
  }

  private void collectDistances(Entity source, int distance, MapPosition pos, Map<MapPosition, Integer> distances,
      Modifiers modifiers) {
    for (MapPosition neighbor : MapTools.getNeighbors(pos)) {
      Influence inf = map.getInfluenceAt(neighbor);
      if (!inf.terrain.moveBlock()) {
        Integer old = distances.get(neighbor);
        int newDistance = distance + max(1, inf.terrain.moveCost() - modifiers.terrainBonus.get(inf.terrain));
        if (old == null || newDistance < old.intValue()) {
          distances.put(neighbor, Integer.valueOf(newDistance));
          if (inf.hasInfluence(source))
            // only one tile from already influenced tiles
            collectDistances(source, newDistance, neighbor, distances, modifiers);
        }
      }
    }
  }

}
