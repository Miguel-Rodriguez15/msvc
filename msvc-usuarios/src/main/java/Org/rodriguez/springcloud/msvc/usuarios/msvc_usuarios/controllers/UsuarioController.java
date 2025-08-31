package Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.controllers;

import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.models.entity.Usuario;
import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class UsuarioController {
    @Autowired
    private UsuarioService service;


    @GetMapping
    public List<Usuario> listar(){
        return  service.listar();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id){
        Optional<Usuario> usuarioOptional = service.porId(id);
        if(usuarioOptional.isPresent())
            return  ResponseEntity.ok().body(usuarioOptional.get());

        return ResponseEntity.notFound().build();
    }
    @PostMapping
    public ResponseEntity<?> crear(@Validated @RequestBody Usuario usuario, BindingResult result){
        if(!usuario.getEmail().isEmpty() && service.porEmail(usuario.getEmail()).isPresent()){
            return ResponseEntity.badRequest().body(Collections.singletonMap("mensaje","ya existe un usuario con este correo!"));
        }
        if(result.hasErrors()){
            return validar(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.guardar(usuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@Validated@ RequestBody Usuario usuario, BindingResult result, @PathVariable Long id){
        if(result.hasErrors()){
            return validar(result);
        }
   Optional<Usuario> optional = service.porId(id);
   if(optional.isPresent()) {
       Usuario usuarioDb = optional.get();
       if(!usuario.getEmail().equalsIgnoreCase(usuarioDb.getEmail()) && service.porEmail(usuario.getEmail()).isPresent()){
           return ResponseEntity.badRequest().body(Collections.singletonMap("mensaje","ya existe un usuario con este correo!"));
       }

       usuarioDb.setNombre(usuario.getNombre());
       usuarioDb.setEmail(usuario.getEmail());
       usuarioDb.setPassword(usuario.getPassword());
       return  ResponseEntity.status(HttpStatus.CREATED).body(service.guardar((usuarioDb)));
   }
        return ResponseEntity.notFound().build();

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id){
        Optional<Usuario> optionalUsuario = service.porId(id);
        if(optionalUsuario.isPresent()){
            service.eliminar(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private  ResponseEntity<Map<String, String>> validar(BindingResult result) {
        Map<String, String> errores = new HashMap<>();
        result.getFieldErrors().forEach(err->{
            errores.put(err.getField(),"El campo " + err.getField() + " " + err.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errores);
    }

    @GetMapping("/usuarios-por-curso")
    public  ResponseEntity<?> obtenerAlumnosPorCurso(@RequestParam List<Long> ids){
        return ResponseEntity.ok(service.ListarPorIds(ids));
    }
}
