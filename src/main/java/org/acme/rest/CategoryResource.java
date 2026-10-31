package org.acme.rest;

import java.util.List;

import org.acme.exception.CategoryBusinessException;
import org.acme.exception.CategoryNotFoundException;
import org.acme.model.Category;
import org.acme.model.CategoryPattern;
import org.acme.model.Transaction;
import org.acme.rest.dto.CategoryRequestDTO;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Categorias", description = "Gerenciamento de categorias e padrões aprendidos de categorização automática")
public class CategoryResource {
    
    @GET 
    @Operation(summary = "Listar categorias", description = "Lista todas as categorias cadastradas.")
    public List<Category> listAll() {
        return Category.listAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar categoria por ID", description = "Retorna uma categoria específica pelo seu identificador.")
    public Category findById(@PathParam("id") Long id) {
        Category category = Category.findById(id);
        if (category == null) {
            throw new CategoryNotFoundException(Response.Status.NOT_FOUND, "Categoria não encontrada para o ID informado");
        }
        return category;
    }

    @POST 
    @Transactional 
    @Operation(summary = "Criar categoria", description = "Cria uma nova categoria com nome único.")
    public Response create(@Valid CategoryRequestDTO dto) {
        String cleanName = dto.name.trim();
        Category existing = Category.find("lower(name)", cleanName.toLowerCase()).firstResult();
        if (existing != null) {
            throw new CategoryBusinessException(Response.Status.BAD_REQUEST, "Categoria já existe para o nome informado");
        }
        Category category = new Category();
        category.name = cleanName;
        category.persist();

        return Response.status(Response.Status.CREATED).entity(category).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar categoria", description = "Atualiza o nome de uma categoria existente.")
    public Category update(@PathParam("id") Long id, @Valid CategoryRequestDTO dto) {
        Category category = Category.findById(id);
        if (category == null) {
            throw new CategoryNotFoundException(Response.Status.NOT_FOUND, "Categoria não encontrada para o ID informado");
        }

        String cleanName = dto.name.trim();
        Category existing = Category.find("lower(name) = ?1 and id != ?2", cleanName.toLowerCase(), id).firstResult();
        if (existing != null) {
            throw new CategoryBusinessException(Response.Status.BAD_REQUEST, "Categoria já existe para o nome informado");
        }

        category.name = cleanName;
        return category;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(
        summary = "Excluir categoria",
        description = "Remove uma categoria caso não existam transações vinculadas a ela, limpando também seus padrões aprendidos."
    )
    public Response delete(@PathParam("id") Long id) {
        Category category = Category.findById(id);
        if (category == null) {
            throw new CategoryNotFoundException(Response.Status.NOT_FOUND, "Categoria não encontrada para o ID informado");
        }

        long transactionsCount = Transaction.count("category.id", id);
        if (transactionsCount > 0) {
            throw new CategoryBusinessException(Response.Status.CONFLICT, "Não é possível excluir uma categoria que possui transações vinculadas");
        }

        CategoryPattern.delete("category.id", id);
        category.delete();
        return Response.noContent().build();
    }

    @GET
    @Path("/patterns")
    @Operation(summary = "Listar padrões aprendidos", description = "Lista todos os padrões de descrição aprendidos automaticamente pelo sistema.")
    public List<CategoryPattern> listPatterns() {
        return CategoryPattern.listAll();
    }

    @DELETE
    @Path("/patterns/{id}")
    @Transactional
    @Operation(summary = "Excluir padrão aprendido", description = "Remove um padrão específico de categorização automática.")
    public Response deletePattern(@PathParam("id") Long id) {
        CategoryPattern pattern = CategoryPattern.findById(id);
        if (pattern == null) {
            throw new CategoryNotFoundException(Response.Status.NOT_FOUND, "Padrão de categorização não encontrado para o ID informado");
        }
        pattern.delete();
        return Response.noContent().build();
    }
}
