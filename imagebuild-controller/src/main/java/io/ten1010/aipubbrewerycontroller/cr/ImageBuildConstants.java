package io.ten1010.aipubbrewerycontroller.cr;

public final class ImageBuildConstants {

    public static final String GROUP = "brewery.aipub.ten1010.io";
    public static final String VERSION = "v1alpha1";
    public static final String PLURAL = "imagebuilds";
    public static final String KIND = "ImageBuild";
    public static final String API_VERSION = GROUP + "/" + VERSION;

    public static final String IMAGE_REGISTRY_SECRET_PREFIX = "image-registry-secret-project-aipub-ten1010-io-";

    public static final String PHASE_PENDING = "Pending";
    public static final String PHASE_PREPARING = "Preparing";
    public static final String PHASE_BUILDING = "Building";
    public static final String PHASE_SUCCEEDED = "Succeeded";
    public static final String PHASE_FAILED = "Failed";

    private ImageBuildConstants() {
    }

}
