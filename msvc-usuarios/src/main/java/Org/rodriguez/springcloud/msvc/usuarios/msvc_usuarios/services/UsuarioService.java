package Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.services;

import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.models.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioService {
    List<Usuario> listar();
    Optional<Usuario> porId(Long id);
    Usuario guardar(Usuario usuario);
    void eliminar(Long id);
    Optional<Usuario> porEmail(String email);
    List<Usuario> ListarPorIds(Iterable<Long> ids);

}
