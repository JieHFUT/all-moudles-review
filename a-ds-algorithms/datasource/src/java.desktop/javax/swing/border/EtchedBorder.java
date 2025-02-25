/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package javax.swing.border;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Color;
import java.awt.Component;
import java.beans.ConstructorProperties;

import com.sun.java.swing.SwingUtilities3;

/**
 * A class which implements a simple etched border which can
 * either be etched-in or etched-out.  If no highlight/shadow
 * colors are initialized when the border is created, then
 * these colors will be dynamically derived from the background
 * color of the component argument passed into the paintBorder()
 * method.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author David Kloba
 * @author Amy Fowler
 */
@SuppressWarnings("serial") // Same-version serialization only
public class EtchedBorder extends AbstractBorder
{
    /** Raised etched type. */
    public static final int RAISED  = 0;
    /** Lowered etched type. */
    public static final int LOWERED = 1;

    /**
     * The type of etch to be drawn by the border.
     */
    protected int etchType;
    /**
     * The color to use for the etched highlight.
     */
    protected Color highlight;
    /**
     * The color to use for the etched shadow.
     */
    protected Color shadow;

    /**
     * Creates a lowered etched border whose colors will be derived
     * from the background color of the component passed into
     * the paintBorder method.
     */
    public EtchedBorder()    {
        this(LOWERED);
    }

    /**
     * Creates an etched border with the specified etch-type
     * whose colors will be derived
     * from the background color of the component passed into
     * the paintBorder method.
     *
     * @param etchType the type of etch to be drawn by the border
     */
    public EtchedBorder(int etchType)    {
        this(etchType, null, null);
    }

    /**
     * Creates a lowered etched border with the specified highlight and
     * shadow colors.
     *
     * @param highlight the color to use for the etched highlight
     * @param shadow the color to use for the etched shadow
     */
    public EtchedBorder(Color highlight, Color shadow)    {
        this(LOWERED, highlight, shadow);
    }

    /**
     * Creates an etched border with the specified etch-type,
     * highlight and shadow colors.
     *
     * @param etchType the type of etch to be drawn by the border
     * @param highlight the color to use for the etched highlight
     * @param shadow the color to use for the etched shadow
     */
    @ConstructorProperties({"etchType", "highlightColor", "shadowColor"})
    public EtchedBorder(int etchType, Color highlight, Color shadow)    {
        this.etchType = etchType;
        this.highlight = highlight;
        this.shadow = shadow;
    }

    private void paintBorderHighlight(Graphics g, Color c, int w, int h, int stkWidth) {
        g.setColor(c);
        g.drawRect(stkWidth/2, stkWidth/2, w-(2*stkWidth), h-(2*stkWidth));
    }

    private void paintBorderShadow(Graphics g, Color c, int w, int h, int stkWidth) {
        g.setColor(c);
        g.drawLine(((3*stkWidth)/2), h-((3*stkWidth)/2), ((3*stkWidth)/2), ((3*stkWidth)/2)); // left line
        g.drawLine(((3*stkWidth)/2), ((3*stkWidth)/2), w-((3*stkWidth)/2), ((3*stkWidth)/2)); // top line

        g.drawLine((stkWidth/2), h-(stkWidth-stkWidth/2),
                w-(stkWidth-stkWidth/2), h-(stkWidth-stkWidth/2)); // bottom line
        g.drawLine(w-(stkWidth-stkWidth/2), h-(stkWidth-stkWidth/2),
                w-(stkWidth-stkWidth/2), stkWidth/2); // right line
    }

    /**
     * Paints the border for the specified component with the
     * specified position and size.
     *
     * @param c the component for which this border is being painted
     * @param g the paint graphics
     * @param x the x position of the painted border
     * @param y the y position of the painted border
     * @param width the width of the painted border
     * @param height the height of the painted border
     */
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        SwingUtilities3.paintBorder(c, g,
                                    x, y,
                                    width, height,
                                    this::paintUnscaledBorder);
    }

    private void paintUnscaledBorder(Component c, Graphics g,
                                     int w, int h,
                                     double scaleFactor) {
        int stkWidth = (int) Math.floor(scaleFactor);
        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setStroke(new BasicStroke((float) stkWidth));
        }

        paintBorderShadow(g, (etchType == LOWERED) ? getHighlightColor(c)
                                                   : getShadowColor(c),
                          w, h, stkWidth);
        paintBorderHighlight(g, (etchType == LOWERED) ? getShadowColor(c)
                                                      : getHighlightColor(c),
                             w, h, stkWidth);
    }

    /**
     * Reinitialize the insets parameter with this Border's current Insets.
     *
     * @param c the component for which this border insets value applies
     * @param insets the object to be reinitialized
     */
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(2, 2, 2, 2);
        return insets;
    }

    /**
     * Returns whether or not the border is opaque.
     * This implementation returns true.
     *
     * @return true
     */
    public boolean isBorderOpaque() { return true; }

    /**
     * Returns which etch-type is set on the etched border.
     *
     * @return the etched border type, either {@code RAISED} or {@code LOWERED}
     */
    public int getEtchType() {
        return etchType;
    }

    /**
     * Returns the highlight color of the etched border
     * when rendered on the specified component.  If no highlight
     * color was specified at instantiation, the highlight color
     * is derived from the specified component's background color.
     *
     * @param c the component for which the highlight may be derived
     * @return the highlight {@code Color} of this {@code EtchedBorder}
     * @since 1.3
     */
    public Color getHighlightColor(Component c)   {
        return highlight != null? highlight :
                                       c.getBackground().brighter();
    }

    /**
     * Returns the highlight color of the etched border.
     * Will return null if no highlight color was specified
     * at instantiation.
     *
     * @return the highlight {@code Color} of this {@code EtchedBorder} or null
     *         if none was specified
     * @since 1.3
     */
    public Color getHighlightColor()   {
        return highlight;
    }

    /**
     * Returns the shadow color of the etched border
     * when rendered on the specified component.  If no shadow
     * color was specified at instantiation, the shadow color
     * is derived from the specified component's background color.
     *
     * @param c the component for which the shadow may be derived
     * @return the shadow {@code Color} of this {@code EtchedBorder}
     * @since 1.3
     */
    public Color getShadowColor(Component c)   {
        return shadow != null? shadow : c.getBackground().darker();
    }

    /**
     * Returns the shadow color of the etched border.
     * Will return null if no shadow color was specified
     * at instantiation.
     *
     * @return the shadow {@code Color} of this {@code EtchedBorder} or null
     *         if none was specified
     * @since 1.3
     */
    public Color getShadowColor()   {
        return shadow;
    }

}
