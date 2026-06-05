package Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.controllers;

import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.models.entity.Usuario;
import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class UsuarioController {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private UsuarioService service;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment env;

    @GetMapping("/crash")
    public void crash(){
        ((ConfigurableApplicationContext)context).close();
    }
    @GetMapping
    public ResponseEntity<?> listar(){
        Map<String, Object> body = new HashMap<>();
        body.put("users", service.listar());
        body.put("pod_info", env.getProperty("MY_POD_NAME") + ": " + env.getProperty("MY_POD_IP"));
        body.put("texto", env.getProperty("config.texto"));
        //return Collections.singletonMap("usuarios",service.listar());
        return ResponseEntity.ok().body(body);
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
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));

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
       usuarioDb.setPassword(passwordEncoder.encode(usuario.getPassword()));
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

    @GetMapping("/oauth2/authorization/msvc-usuarios-client")
    public void authorize(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String redirectUri = URLEncoder.encode(baseUrl + "/authorized", StandardCharsets.UTF_8);
        String authUrl = env.getProperty("LB_AUTH_ISSUER_URI", "http://127.0.0.1:9000")
                + "/oauth2/authorize?response_type=code&client_id=usuarios-client"
                + "&redirect_uri=" + redirectUri
                + "&scope=openid%20read%20write";
        response.sendRedirect(authUrl);
    }

    @GetMapping("/authorized")
    public Map<String, Object> authorized(@RequestParam(name = "code") String code) {
      return  Collections.singletonMap("code",code);
    }

    @GetMapping("/login")
    public ResponseEntity<?> loginByEmail(@RequestParam String email) {
        Optional<Usuario> o = service.porEmail(email);
                if(o.isPresent()){
                    return ResponseEntity.ok(o.get());
                }
        return  ResponseEntity.notFound().build();
    }


}
