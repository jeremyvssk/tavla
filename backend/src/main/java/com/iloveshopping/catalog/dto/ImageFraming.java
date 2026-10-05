// How a photo sits on its tile, measured by tools/catalog-import/frame_photos.py; see that script for each field.
package com.iloveshopping.catalog.dto;

import java.util.List;

/**
 * @param ratio width / height of the image
 * @param box   [x0, y0, x1, y1] as fractions of the image: the part the tile frames
 * @param bleed the sides where the photo is cut ("t", "b", "l", "r"), to be put on the tile's edge
 * @param lift  brightness that turns an off-white ground into white; 1 for none
 * @param print shown whole as a flat picture (a book cover, a page) rather than bled or trimmed
 * @param focus [x, y] as fractions of the box: the middle of the product, kept in view when the tile crops
 */
public record ImageFraming(double ratio, List<Double> box, String bleed, double lift, boolean print, List<Double> focus) {
}
