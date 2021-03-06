package com.galvarez.ttw.rendering;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.galvarez.ttw.model.map.MapTools;
import com.galvarez.ttw.rendering.components.FadingMessage;
import com.galvarez.ttw.rendering.components.MutableMapPosition;
import com.galvarez.ttw.utils.FloatPair;
import com.galvarez.ttw.utils.Font;

@Wire
public final class FadingMessageRenderSystem extends EntityProcessingSystem {

  private ComponentMapper<MutableMapPosition> mmpm;

  private ComponentMapper<FadingMessage> fmm;

  private final SpriteBatch batch;

  private final OrthographicCamera camera;

  private final BitmapFont font;

  @SuppressWarnings("unchecked")
  public FadingMessageRenderSystem(OrthographicCamera camera, SpriteBatch batch) {
    super(Aspect.getAspectForAll(MutableMapPosition.class, FadingMessage.class));
    this.batch = batch;
    this.camera = camera;

    font = Font.IRIS_UPC.get();
    font.setUseIntegerPositions(false);
  }

  @Override
  protected void begin() {
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.setColor(1, 1, 1, 1);
  }

  @Override
  protected void process(Entity e) {
    MutableMapPosition position = mmpm.get(e);
    FadingMessage message = fmm.get(e);

    FloatPair drawPosition = MapTools.world2window(position.x, position.y);
    float posX = drawPosition.x - message.label.length() * font.getSpaceWidth();
    float posY = drawPosition.y;

    Color color = message.color;
    font.setColor(color.r, color.g, color.b, 1 - message.currentTime / message.duration);
    font.draw(batch, message.label, posX, posY);

    position.x += message.vx * world.getDelta();
    position.y += message.vy * world.getDelta();
    message.currentTime += world.getDelta();

    if (message.currentTime >= message.duration)
      e.deleteFromWorld();
  }

  @Override
  protected void end() {
    batch.end();
  }

}
