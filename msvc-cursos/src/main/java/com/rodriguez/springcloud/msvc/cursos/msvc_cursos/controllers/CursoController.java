package com.rodriguez.springcloud.msvc.cursos.msvc_cursos.controllers;

import com.rodriguez.springcloud.msvc.cursos.msvc_cursos.models.Usuario;
import com.rodriguez.springcloud.msvc.cursos.msvc_cursos.models.entity.Curso;
import com.rodriguez.springcloud.msvc.cursos.msvc_cursos.services.CursoService;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController()
public class CursoController {

    @Autowired
    private CursoService service;

    @GetMapping
    public ResponseEntity<List<Curso>> listar(){
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id){
        Optional<Curso> o = service.porIdConUsuarios(id);//porId
        if(o.isPresent()){
            return  ResponseEntity.ok(o.get());

        }else
            return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> crear( @RequestBody Curso curso){
        Curso cursoDb = service.guardar(curso);
        return ResponseEntity.status(HttpStatus.CREATED).body(cursoDb);

    }
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@RequestBody Curso curso, @PathVariable Long id){

        Optional<Curso> o = service.porId(id);
        if (o.isPresent()){
            Curso cursoDb = o.get();
            cursoDb.setNombre(curso.getNombre());
            return ResponseEntity.status(HttpStatus.CREATED).body(service.guardar(cursoDb));

        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id){
        Optional<Curso> o = service.porId(id);
        if (o.isPresent()){
            service.eliminar(o.get().getId());
            return ResponseEntity.noContent().build();
        }
        return  ResponseEntity.notFound().build();
    }

    @PutMapping("/asignar-usuario/{cursoId}")
    public ResponseEntity<?> asignarUsuario(@RequestBody Usuario usuario, @PathVariable Long cursoId) {
        Optional<Usuario> usuarioOptional;
        try{
            usuarioOptional = service.asignarUsuario(usuario, cursoId);
        }catch (FeignException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("mensaje", "No existe el " +
                    "usuario por id o error en la comunicación: " + e.getMessage()));
        }

        if (usuarioOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(usuarioOptional.get());

        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/crear-usuario/{cursoId}")
    public ResponseEntity<?> crearUsuario(@RequestBody Usuario usuario, @PathVariable Long cursoId) {
        Optional<Usuario> aux;
        try {
            aux = service.crearUsuario(usuario, cursoId);
        } catch (FeignException ex) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("mensaje", "No se pudo crear el usuario" +
                            "o error en la comunicacion: " + ex.getMessage()));

        }

        if (aux.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(aux.get());

        }
        return ResponseEntity.notFound().build();


    }

    @DeleteMapping("/eliminar-usuario/{cursoId}")
    public ResponseEntity<?> eliminarusuario(@RequestBody Usuario usuario, @PathVariable Long cursoId) {
        Optional<Usuario> aux;
        try {
            aux = service.eliminarUsuario(usuario, cursoId);
        } catch (FeignException ex) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("mensaje", "No se pudo eliminar el usuario" +
                            "o error en la comunicacion: " + ex.getMessage()));

        }

        if (aux.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(aux.get());

        }
        return ResponseEntity.notFound().build();

    }
    @DeleteMapping("/eliminar-curso-usuario/{id}")
    public ResponseEntity<?> eliminarCursoUsuarioPorId( @PathVariable Long id) {
        service.eliminarCursoUsuarioPorId(id);
        return ResponseEntity.noContent().build();

    }

    private  ResponseEntity<Map<String, String>> validar(BindingResult result) {
        Map<String, String> errores = new HashMap<>();
        result.getFieldErrors().forEach(err->{
            errores.put(err.getField(),"El campo " + err.getField() + " " + err.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errores);
    }
}
