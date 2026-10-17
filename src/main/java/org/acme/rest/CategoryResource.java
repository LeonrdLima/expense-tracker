package org.acme.rest;

import java.util.List;

import org.acme.model.Category;
import org.acme.rest.dto.CategoryRequestDTO;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CategoryResource {
    
    @GET 
    public List<Category>listAll() {
        return Category.listAll();
    }

    @POST 
    @Transactional 
    public Response create(@Valid CategoryRequestDTO dto) {
        Category existing = Category.find("name", dto.name).firstResult();
        if(existing != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Categoria já existe para o nome informado")
                           .build();
        }
        Category category = new Category();
        category.name = dto.name;
        category.persist();

        return Response.status(Response.Status.CREATED).entity(category).build();
    }
}
