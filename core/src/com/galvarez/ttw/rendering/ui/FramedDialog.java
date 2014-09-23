package com.galvarez.ttw.rendering.ui;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence;
import static java.lang.Math.max;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class FramedDialog {

  private final Dialog dialog;

  private final Image frame;

  private final Skin skin;

  public FramedDialog(Skin skin, String title, String message) {
    this.skin = skin;

    // TODO find why title is outside the box
    dialog = new Dialog(title, skin);
    dialog.setBackground(skin.getTiledDrawable("menuTexture"));
    dialog.getContentTable().defaults().expandX().fillX();
    dialog.getButtonTable().defaults().width(128).fillX();

    Label label = new Label(message, skin);
    label.setAlignment(Align.center);
    label.setWrap(true);

    dialog.text(label);

    frame = new Image(skin.getPatch("frame"));
  }

  public void setKey(int key, Object result) {
    dialog.key(key, result);
  }

  public void addButton(String text, ChangeListener changeListener) {
    TextButton button = new TextButton(text, skin);
    button.addListener(changeListener);

    button.addListener(new ChangeListener() {
      @Override
      public void changed(ChangeEvent event, Actor actor) {
        frame.addAction(sequence(fadeOut(0.5f, Interpolation.fade), Actions.removeActor()));
      }
    });
    dialog.button(button);
  }

  public void addButton(Button button) {
    button.addListener(new ChangeListener() {
      @Override
      public void changed(ChangeEvent event, Actor actor) {
        frame.addAction(sequence(fadeOut(0.5f, Interpolation.fade), Actions.removeActor()));
      }
    });
    dialog.button(button);
  }

  public void addToStage(Stage stage, int minWidth, int minHeight) {
    stage.addActor(dialog);
    stage.addActor(frame);

    int width = max(minWidth, (int) dialog.getPrefWidth());
    int height = max(minHeight, (int) dialog.getPrefHeight());
    dialog.setWidth(width);
    dialog.setHeight(height);
    int x = (int) (stage.getWidth() / 2 - width / 2);
    int y = (int) (stage.getHeight() / 2 - height / 2);
    dialog.setX(x);
    dialog.setY(y);
    frame.setX(x - 1);
    frame.setY(y - 3);
    frame.setWidth(width + 4);
    frame.setHeight(height + 4);

    frame.setTouchable(Touchable.disabled);
  }
}