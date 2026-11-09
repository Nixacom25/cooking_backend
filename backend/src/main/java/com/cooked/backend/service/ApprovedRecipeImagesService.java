package com.cooked.backend.service;

import java.io.IOException;
import java.io.OutputStream;

/** Exports the photos of the recipes approved after an intern's edits. */
public interface ApprovedRecipeImagesService {

    /** Number of approved recipes that have a photo. */
    int countApprovedWithImage();

    /**
     * Writes a .zip holding one file per approved recipe photo plus a {@code manifest.csv}
     * (recipe id, name, image URL, file name or the reason it is missing). Photos that cannot be
     * downloaded are listed in the manifest instead of failing the whole export.
     */
    void writeZip(OutputStream out) throws IOException;
}
