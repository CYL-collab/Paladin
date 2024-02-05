package com.sei.bean.Collection.Graph;
import com.sei.bean.View.Action;
import org.jgrapht.graph.*;

public class ActionEdge extends DefaultEdge
{
    public String path;
    public String content;
    public int action;

    /**
     * Constructs an action edge
     *
     * @param path the xpath and number of the widget.
     *
     */
    public ActionEdge(Action action)
    {
        this.path = action.getPath();
        this.content = action.getContent();
        this.action = action.getAction();
    }

    @Override
    public String toString()
    {
        return "(" + getSource() + " : " + getTarget() + " : " + path + ")";
    }
}
