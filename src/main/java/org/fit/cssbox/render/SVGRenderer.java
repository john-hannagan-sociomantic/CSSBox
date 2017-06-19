/*
 * SVGRenderer.java
 * Copyright (c) 2005-2013 Radek Burget
 *
 * CSSBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CSSBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with CSSBox. If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 8.3.2013, 14:16:05 by burgetr
 */
package org.fit.cssbox.render;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

import javax.imageio.ImageIO;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.TermColor;

import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.BlockBox;
import org.fit.cssbox.layout.Box;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.LengthSet;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.ReplacedContent;
import org.fit.cssbox.layout.ReplacedImage;
import org.fit.cssbox.layout.ReplacedText;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.layout.Viewport;
import org.fit.cssbox.layout.VisualContext;
import org.fit.cssbox.misc.Base64Coder;

/**
 * A renderer that produces an SVG output.
 *
 * @author burgetr
 */
public class SVGRenderer implements BoxRenderer
{
    private PrintWriter out;

    private int rootw;
    private int rooth;

    private int idcounter;

    public SVGRenderer(int rootWidth, int rootHeight, Writer out)
    {
        idcounter = 1;
        rootw = rootWidth;
        rooth = rootHeight;
        this.out = new PrintWriter(out);
        writeHeader();

        System.out.println( "Using new renderer" );
    }

    //====================================================================================================

    private boolean hasClass(ElementBox elem, String className)
    {
        if( elem.getElement().hasAttribute( "class" ) )
        {
            List<String> arr = java.util.Arrays.asList(
                elem.getElement()
                .getAttribute( "class" )
                .split( " " ) );

            return arr.contains( className );

        }
        return false;
    }

    private String getElementName(ElementBox elem)
    {
        if( hasClass(elem, "products" ) )
            return "$products";
        if( hasClass(elem, "item" ) )
            return "$item";
        if( hasClass(elem, "metadata-manufacturer" ) )
            return "$manufacturer";
        if( hasClass(elem, "metadata-title" ) )
            return "$title";
        if( hasClass(elem, "metadata-c2a" ) )
            return "$c2a";
        if( hasClass(elem, "metadata-price" ) )
            return "$price";
        if( hasClass(elem, "banner-item-image" ) )
            return "$item-image";
        if( hasClass(elem, "logo" ) )
            return "$logo";
        if( hasClass(elem, "usp" ) )
            return "$usp";
        if( hasClass(elem, "metadata-text" ) )
            return "$text";
        if( hasClass(elem, "metadata-sale-price" ) )
            return "$sale-price";
        if( hasClass(elem, "metadata-old-price" ) )
            return "$old-price";
        if( hasClass(elem, "metadata-sale-icon" ) )
            return "$sale-icon";
        if( hasClass(elem, "metadata-new-icon" ) )
            return "$new-icon";

        if( isTopLevelBannerElement( elem ) )
            return "$" + getBannerSizeString(elem);

        // If we can't find an exact match, return
        // the full class list (but not if its just Xanonymous).
        String fullClassList = elem.getElement().getAttribute( "class" );
        return fullClassList == "Xanonymous" ? "" : fullClassList;
    }
    private String getBannerSizeString(ElementBox elem)
    {
        if( elem.getElement().hasAttribute( "class" ) )
        {
            String[] classes =
                elem.getElement()
                .getAttribute( "class" )
                .split( " " );

            for( String c : classes )
            {
                if( c.startsWith( "banner-size-" ) )
                    return c;
            }
        }
        return null;
    }
    private boolean isTopLevelBannerElement(ElementBox elem)
    {
        return hasClass( elem, "product-banner" );
    }

    public void startElementContents(ElementBox elem)
    {
        if (elem instanceof BlockBox && ((BlockBox) elem).getOverflow() != BlockBox.OVERFLOW_VISIBLE)
        {
            // Work out how many parent elements we have
            // int parentCount = 0;
            // Box parent = elem;
            // do
            // {
            //     parent = parent.getParent();
            //     if( parent != null )
            //     {
            //         parentCount ++;
            //     }
            // } while( parent != null );

            out.println( "<!-- Class attribute [" + elem.getElement().getAttribute( "class" ) + "] -->" );


            // If we have 4, we are pobably the top level banner rectangle/group
            // So we should make a clippath, so that our deals aren't hanging
            // over the edge of the banners.
            if( isTopLevelBannerElement( elem ) )
            {
                String bannerSize = getBannerSizeString( elem );

                Rectangle cb = elem.getClippedContentBounds();
                String clip = "cssbox-clip-" + idcounter;
                out.print("<clipPath id=\"" + clip + "\">");
                out.print("<rect x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" />");
                out.println("</clipPath>");
                out.println("<g data-name=\"" + bannerSize + "\" id=\"cssbox-obj-" + idcounter + "\" clip-path=\"url(#" + clip + ")\">");
            }
            else
            {
                // For blocks with overflow not visible, put them in a group.
                // We can kind of think like these as groups of elements (parent, children etc)
                String name = getElementName( elem );
                String dataName = name != "" ? " data-name=\"" + name + "\" " : "";

                out.println("<g " + dataName + " id=\"cssbox-obj-" + idcounter + "\">");
            }

            idcounter++;
        }
    }

    public void finishElementContents(ElementBox elem)
    {
        if (elem instanceof BlockBox && ((BlockBox) elem).getOverflow() != BlockBox.OVERFLOW_VISIBLE)
        {
            //for blocks with overflow != visible finish the clipping group
            out.println("</g>");
        }
    }

    public void renderElementBackground(ElementBox eb)
    {
        Rectangle bb = eb.getAbsoluteBorderBounds();
        if (eb instanceof Viewport)
        {
            bb = eb.getClippedBounds(); //for the root box (Viewport), use the whole clipped content
            out.println( "<!-- is instance of viewport: using getClippedBounds -->" );
        }
        Color bg = eb.getBgcolor();


        // Uncomment this to print out the node depth
        // int parentCount = 0;
        // Box parent = eb;
        // do
        // {
        //     parent = parent.getParent();
        //     if( parent != null )
        //     {
        //         parentCount ++;
        //     }
        // } while( parent != null );
        // out.println( "<!-- Num parents: " + parentCount + " -->" );

        LengthSet borders = eb.getBorder();
        boolean hasAllBorders = borders.top > 0 &&
                                borders.right > 0 &&
                                borders.bottom > 0 &&
                                borders.left > 0 &&
                                ( borders.top == borders.right && borders.top == borders.left && borders.top == borders.bottom );

        //background color
        if (bg != null)
        {
            // If it has a BG colour, then its NOT going to have
            // borders :(
            // Hover, I think this is the shape we want to draw eventually..

            // String stroke = "stroke:none;";
            // if( hasAllBorders )
            // {
            //     stroke = "stroke:black;stroke-width:10px;";
            // }
            String name = getElementName( eb );
            String dataName = name != "" ? " data-name=\"" + name + "\" " : "";

            String style = "fill-opacity:1;fill:" + colorString(bg);
            out.println("<rect " + dataName + " x=\"" + bb.x + "\" y=\"" + bb.y + "\" width=\"" + bb.width + "\" height=\"" + bb.height + "\" style=\"" + style + "\" />");
        }
        else
        {

            // This is how we import a rectangle with a border which only goes INWARDS.
            // We must group the rect, and set the stroke on the group.
            // If we only set the stroke on the rectangle then the border will go both ways,
            // outwards + inwards.
            //
            // .cls-1 {
            //     fill: #fff;
            //     stroke: red;
            //     stroke-width: 10px;
            // }
            // .cls-2 {
            //     stroke: none;
            // }
            // .cls-3 {
            //     fill: none;
            // }
            // <g id="Rectangle_343" data-name="Rectangle 343" class="cls-1" transform="translate(2120 864)">
            //     <rect class="cls-2" width="37" height="44"/>
            //     <rect class="cls-3" x="5" y="5" width="27" height="34"/>
            // </g>
            if( hasAllBorders )
            {
                String color = getBorderColourString( eb );
                String stroke = "stroke:" + color + ";stroke-width:" + borders.top + "px;";
                String style = stroke + "fill:white;";

                int boxWidth = eb.getContentWidth();
                int boxHeight = eb.getContentHeight();

                int innerWidth = boxWidth - borders.top;
                int innerHeight = boxHeight - borders.top;

                double inset = borders.top / 2.0;

                String name = getElementName( eb );
                String dataName = name != "" ? " data-name=\"" + name + "\" " : "";

                out.println( "<g " + dataName + " style=\"" + style + "\" transform=\"translate(" + bb.x + " " + bb.y + ")\" >" );
                out.println("<rect width=\"" + boxWidth + "\" height=\"" + boxHeight + "\" style=\"stroke:none;\" />");
                out.println("<rect x=\"" + inset + "\" y=\"" + inset + "\" width=\"" + innerWidth + "\" height=\"" + innerHeight + "\"  style=\"fill:none;\"  />");
                out.println( "</g>" );
            }
        }

        //background image
        if (eb.getBackgroundImages() != null && eb.getBackgroundImages().size() > 0)
        {
            for (BackgroundImage bimg : eb.getBackgroundImages())
            {
                BufferedImage img = bimg.getBufferedImage();
                if (img != null)
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try
                    {
                        ImageIO.write(img, "png", os);
                    } catch (IOException e) {
                        out.println("<!-- I/O error: " + e.getMessage() + " -->");
                    }
                    char[] data = Base64Coder.encode(os.toByteArray());
                    String imgdata = "data:image/png;base64," + new String(data);
                    int ix = bb.x + eb.getBorder().left;
                    int iy = bb.y + eb.getBorder().top;
                    int iw = bb.width - eb.getBorder().right - eb.getBorder().left;
                    int ih = bb.height - eb.getBorder().bottom - eb.getBorder().top;

                    String name = getElementName( eb );
                    String dataName = name != "" ? " data-name=\"" + name + "\" " : "";

                    out.println("<image " + dataName + " x=\"" + ix + "\" y=\"" + iy + "\" width=\"" + iw + "\" height=\"" + ih + "\" xlink:href=\"" + imgdata + "\" />");
                }
            }

        }

        //border
        if( !hasAllBorders )
        {
            if (borders.top > 0)
                writeBorderSVG(eb, bb.x, bb.y, bb.x + bb.width, bb.y, "top", borders.top, 0, borders.top/2);
            if (borders.right > 0)
                writeBorderSVG(eb, bb.x + bb.width, bb.y, bb.x + bb.width, bb.y + bb.height, "right", borders.right, -borders.right/2, 0);
            if (borders.bottom > 0)
                writeBorderSVG(eb, bb.x, bb.y + bb.height, bb.x + bb.width, bb.y + bb.height, "bottom", borders.bottom, 0, -borders.bottom/2);
            if (borders.left > 0)
                writeBorderSVG(eb, bb.x, bb.y, bb.x, bb.y + bb.height, "left", borders.left, borders.left/2, 0);
        }
    }

    public void renderTextContent(TextBox text)
    {
        Rectangle b = text.getAbsoluteBounds();
        VisualContext ctx = text.getVisualContext();

        String style = "font-size:" + ctx.getFontSize() + "pt;" +
                       "font-weight:" + (ctx.getFont().isBold()?"bold":"normal") + ";" +
                       "font-variant:" + (ctx.getFont().isItalic()?"italic":"normal") + ";" +
                       "font-family:" + ctx.getFont().getFamily() + ";" +
                       "fill:" + colorString(ctx.getColor()) + ";" +
                       "stroke:none";

        if (!ctx.getTextDecoration().isEmpty())
            style += ";text-decoration:" + ctx.getTextDecorationString();

        out.println("<text x=\"" + b.x + "\" y=\"" + (b.y + text.getBaselineOffset()) + "\" width=\"" + b.width + "\" height=\"" + b.height + "\" style=\"" + style + "\">" + htmlEntities(text.getText()) + "</text>");
    }

    public void renderReplacedContent(ReplacedBox box)
    {
        ReplacedContent cont = box.getContentObj();
        if (cont != null)
        {
            if (cont instanceof ReplacedImage) //images
            {
                BufferedImage img = ((ReplacedImage) cont).getBufferedImage();
                if (img != null)
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try
                    {
                        ImageIO.write(img, "png", os);
                    } catch (IOException e) {
                        out.println("<!-- I/O error: " + e.getMessage() + " -->");
                    }
                    char[] data = Base64Coder.encode(os.toByteArray());
                    String imgdata = "data:image/png;base64," + new String(data);
                    Rectangle cb = ((Box) box).getAbsoluteContentBounds();

                    // TODO: This needs to also add the data name. how do we get the Element?
                    String name = getElementName( cont.getOwner() );
                    String dataName = name != "" ? " data-name=\"" + name + "\" " : "";

                    out.println("<image " + dataName + " x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" xlink:href=\"" + imgdata + "\" />");
                }
            }
            else if (cont instanceof ReplacedText) //HTML object
            {
                //clipping rectangle for the object
                Rectangle cb = ((Box) box).getClippedBounds();
                String clip = "cssbox-clip-" + idcounter;
                out.print("<clipPath id=\"" + clip + "\">");
                out.print("<rect x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" />");
                out.println("</clipPath>");

                //the group containing the rendered object
                out.println("<g id=\"cssbox-obj-" + (idcounter++) + "\" clip-path=\"url(#" + clip + ")\">");
                ReplacedText rt = (ReplacedText) cont;
                rt.getContentViewport().draw(this);
                out.println("</g>");
            }
        }
    }

    public void close()
    {
        writeFooter();
    }

    //====================================================================================================

    private void writeHeader()
    {
        out.println("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>");
        out.println("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 20010904//EN\" \"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd\">");
        out.println("<!-- Rendered by CSSBox http://cssbox.sourceforge.net -->");
        out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        out.println("     xmlns:xlink=\"http://www.w3.org/1999/xlink\" xml:space=\"preserve\"");
        out.println("         width=\"" + rootw + "\" height=\"" + rooth + "px\"");
        out.println("         viewBox=\"0 0 " + rootw + " " + rooth + "\"");
        out.println("         zoomAndPan=\"disable\" >");
    }

    private void writeFooter()
    {
        out.println("</svg>");
    }

    private String getBorderColourString(ElementBox eb)
    {
        CSSProperty.BorderColor bclr = eb.getStyle().getProperty("border-top-color");
        TermColor tclr = eb.getStyle().getValue(TermColor.class, "border-top-color");
        CSSProperty.BorderStyle bst = eb.getStyle().getProperty("border-top-style");

        if (bst != CSSProperty.BorderStyle.HIDDEN && bclr != CSSProperty.BorderColor.TRANSPARENT)
        {
            Color clr = null;
            if (tclr != null)
                clr = tclr.getValue();
            if (clr == null)
            {
                clr = eb.getVisualContext().getColor();
                if (clr == null)
                    clr = Color.BLACK;
            }

            if( clr != null )
            {
                return colorString( clr );
            }
        }
        return null;
    }

    private void writeBorderSVG(ElementBox eb, int x1, int y1, int x2, int y2, String side, int width, int right, int down)
    {
        CSSProperty.BorderColor bclr = eb.getStyle().getProperty("border-"+side+"-color");
        TermColor tclr = eb.getStyle().getValue(TermColor.class, "border-"+side+"-color");
        CSSProperty.BorderStyle bst = eb.getStyle().getProperty("border-"+side+"-style");
        if (bst != CSSProperty.BorderStyle.HIDDEN && bclr != CSSProperty.BorderColor.TRANSPARENT)
        {
            Color clr = null;
            if (tclr != null)
                clr = tclr.getValue();
            if (clr == null)
            {
                clr = eb.getVisualContext().getColor();
                if (clr == null)
                    clr = Color.BLACK;
            }

            String stroke = "";
            if (bst == CSSProperty.BorderStyle.SOLID)
            {
                stroke = "stroke-width:" + width;
            }
            else if (bst == CSSProperty.BorderStyle.DOTTED)
            {
                stroke = "stroke-width:" + width + ";stroke-dasharray:" + width + "," + width;
            }
            else if (bst == CSSProperty.BorderStyle.DASHED)
            {
                stroke = "stroke-width:" + width + ";stroke-dasharray:" + (3*width) + "," + width;
            }
            else if (bst == CSSProperty.BorderStyle.DOUBLE)
            {
                //double is not supported yet, we'll use single
                stroke = "stroke-width:" + width;
            }
            else //default or unsupported - draw a solid line
            {
                stroke = "stroke-width:" + width;
            }

            String coords = "M " + (x1+right) + "," + (y1+down) + " L " + (x2+right) + "," + (y2+down);
            String style = "fill:none;stroke:" + colorString(clr) + ";" + stroke;
            out.println("<path style=\"" + style + "\" d=\"" + coords + "\" />");
        }
    }

    private String colorString(Color color)
    {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String htmlEntities(String s)
    {
        return s.replaceAll("&", "&amp;").replaceAll(">", "&gt;").replaceAll("<", "&lt;");
    }

}
