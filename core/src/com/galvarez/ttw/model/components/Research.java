package com.galvarez.ttw.model.components;

import java.util.List;

import com.artemis.Component;
import com.galvarez.ttw.model.data.Discovery;

public class Research extends Component {

  public final List<String> previous;

  public final Discovery target;

  public Research(List<String> previous, Discovery target) {
    this.previous = previous;
    this.target = target;
  }

}