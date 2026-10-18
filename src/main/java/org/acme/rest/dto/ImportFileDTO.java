package org.acme.rest.dto;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class ImportFileDTO {
    @RestForm("file")
    public FileUpload file;
}
