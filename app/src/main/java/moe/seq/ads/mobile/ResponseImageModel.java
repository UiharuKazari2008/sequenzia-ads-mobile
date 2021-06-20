package moe.seq.ads.mobile;

import java.util.Date;

public class ResponseImageModel {

    private String previewImage;
    private String fullImage;
    private String textContents;
    private Date imageDate;
    private String channelName;
    private String channelClass;
    private String serverName;
    private int messageEid;
    private String imageJumpLink;
    private Boolean imageFavorited;
    private String messageId;
    private String imagePermaLink;
    private int imageSizeHeight;
    private int imageSizeWidth;
    private float imageRatio;
    private int[] imageAdvColor;

    public ResponseImageModel(String previewImage, String fullImage, String textContents, Date imageDate, String channelName, String channelClass, String serverName, int messageEid, String imageJumpLink, Boolean imageFavorited, String messageId, String imagePermaLink, int imageSizeHeight, int imageSizeWidth, float imageRatio, int[] imageAdvColor) {
        this.previewImage = previewImage;
        this.fullImage = fullImage;
        this.textContents = textContents;
        this.imageDate = imageDate;
        this.channelName = channelName;
        this.channelClass = channelClass;
        this.serverName = serverName;
        this.messageEid = messageEid;
        this.imageJumpLink = imageJumpLink;
        this.imageFavorited = imageFavorited;
        this.messageId = messageId;
        this.imagePermaLink = imagePermaLink;
        this.imageSizeHeight = imageSizeHeight;
        this.imageSizeWidth = imageSizeWidth;
        this.imageRatio = imageRatio;
        this.imageAdvColor = imageAdvColor;
    }

    public ResponseImageModel() {

    }

    public String getPreviewImage() {
        return previewImage;
    }

    public void setPreviewImage(String previewImage) {
        this.previewImage = previewImage;
    }

    public String getFullImage() {
        return fullImage;
    }

    public void setFullImage(String fullImage) {
        this.fullImage = fullImage;
    }

    public String getTextContents() {
        return textContents;
    }

    public void setTextContents(String textContents) {
        this.textContents = textContents;
    }

    public Date getImageDate() {
        return imageDate;
    }

    public void setImageDate(Date imageDate) {
        this.imageDate = imageDate;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelClass() {
        return channelClass;
    }

    public void setChannelClass(String channelClass) {
        this.channelClass = channelClass;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getMessageEid() {
        return messageEid;
    }

    public void setMessageEid(int messageEid) {
        this.messageEid = messageEid;
    }

    public String getImageJumpLink() {
        return imageJumpLink;
    }

    public void setImageJumpLink(String imageJumpLink) {
        this.imageJumpLink = imageJumpLink;
    }

    public Boolean getImageFavorited() {
        return imageFavorited;
    }

    public void setImageFavorited(Boolean imageFavorited) {
        this.imageFavorited = imageFavorited;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getImagePermaLink() {
        return imagePermaLink;
    }

    public void setImagePermaLink(String imagePermaLink) {
        this.imagePermaLink = imagePermaLink;
    }

    public int getImageSizeHeight() {
        return imageSizeHeight;
    }

    public void setImageSizeHeight(int imageSizeHeight) {
        this.imageSizeHeight = imageSizeHeight;
    }

    public int getImageSizeWidth() {
        return imageSizeWidth;
    }

    public void setImageSizeWidth(int imageSizeWidth) {
        this.imageSizeWidth = imageSizeWidth;
    }

    public float getImageRatio() {
        return imageRatio;
    }

    public void setImageRatio(float imageRatio) {
        this.imageRatio = imageRatio;
    }

    public int[] getImageAdvColor() {
        return imageAdvColor;
    }

    public void setImageAdvColor(int[] imageAdvColor) {
        this.imageAdvColor = imageAdvColor;
    }
}
