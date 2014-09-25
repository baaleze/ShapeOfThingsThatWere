package com.galvarez.ttw.screens;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;

import com.artemis.World;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galvarez.ttw.ThingsThatWereGame;
import com.galvarez.ttw.model.DiscoverySystem;
import com.galvarez.ttw.model.components.Discoveries;
import com.galvarez.ttw.model.components.Research;
import com.galvarez.ttw.model.data.Choice;
import com.galvarez.ttw.model.data.Discovery;
import com.galvarez.ttw.rendering.ui.FramedMenu;
import com.galvarez.ttw.screens.overworld.OverworldScreen;

/**
 * This screen appears when user tries to pause or escape from the main game
 * screen.
 * 
 * @author Guillaume Alvarez
 */
public final class DiscoveryMenuScreen extends AbstractPausedScreen<OverworldScreen> {

  private final FramedMenu topMenu, empireChoices, discoveryChoices;

  private final Discoveries empire;

  private final DiscoverySystem discoverySystem;

  public DiscoveryMenuScreen(ThingsThatWereGame game, World world, SpriteBatch batch, OverworldScreen gameScreen,
      Discoveries empire, DiscoverySystem discoverySystem) {
    super(game, world, batch, gameScreen);
    this.empire = empire;
    this.discoverySystem = discoverySystem;

    topMenu = new FramedMenu(skin, 800, 600);
    empireChoices = new FramedMenu(skin, 800, 600);
    discoveryChoices = new FramedMenu(skin, 800, 600);
  }

  @Override
  protected void initMenu() {
    topMenu.clear();
    topMenu.addButton("Resume game", new ChangeListener() {
      @Override
      public void changed(ChangeEvent event, Actor actor) {
        resumeGame();
      }
    }, true);
    topMenu.addToStage(stage, 30, stage.getHeight() - 30, false);

    empireChoices.clear();
    addEmpireChoices();
    empireChoices.addToStage(stage, 30, topMenu.getY() - 30, false);

    discoveryChoices.clear();
    for (Research next : discoverySystem.possibleDiscoveries(empire, 5))
      discoveryChoices.addButton("Combine: ", discoverySystem.previousString(empire, next), //
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              empire.nextDiscovery = next;
              resumeGame();
            }
          }, true);
    discoveryChoices.addToStage(stage, 30, empireChoices.getY() - 30, false);
  }

  private static final Discovery NONE = new Discovery("NONE", new ArrayList<String>());

  private void addEmpireChoices() {
    for (Choice choice : Choice.values()) {
      Label l = new Label(choice.msg, skin.get(LabelStyle.class));
      empireChoices.getTable().add(l).minHeight(l.getMinHeight()).prefHeight(l.getPrefHeight());

      SelectBox<Discovery> sb = new SelectBox<Discovery>(skin.get(SelectBoxStyle.class));
      if (empire.choices.containsKey(choice)) {
        sb.setItems(discoveries(empire, choice).toArray(new Discovery[0]));
        sb.setSelected(empire.choices.get(choice));
      } else {
        List<Discovery> possible = discoveries(empire, choice);
        possible.add(NONE);
        sb.setItems(possible.toArray(new Discovery[0]));
        sb.setSelected(NONE);
      }
      sb.addListener(new ChangeListener() {
        @Override
        public void changed(ChangeEvent event, Actor actor) {
          if (sb.getSelected() != NONE)
            empire.choices.put(choice, sb.getSelected());
        }
      });
      empireChoices.getTable().add(sb).minHeight(sb.getMinHeight()).prefHeight(sb.getMinHeight());

      empireChoices.getTable().row();
    }
  }

  private static List<Discovery> discoveries(Discoveries empire, Choice choice) {
    return empire.discovered.stream().filter(d -> d.groups.contains(choice.name())).collect(toList());
  }
}
