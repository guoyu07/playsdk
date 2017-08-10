package sdk.converter.attachment;

/**
 * Created by Orozco on 8/10/17.
 */
public class Attachment extends AbstractAttachment {

    String mimeType;
    String attachmentURL;
    String title;


    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public void setAttachmentURL(String attachmentURL) {
        this.attachmentURL = attachmentURL;
    }

    @Override
    public String getAttachmentURL() {
        return attachmentURL;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getTitle() {
        return title;
    }
}
