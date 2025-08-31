package Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.services;

import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.clients.CursoClienteRest;
import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.models.entity.Usuario;
import Org.rodriguez.springcloud.msvc.usuarios.msvc_usuarios.repositories.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@Service
public class UsuarioServiceImpl implements  UsuarioService{
    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private CursoClienteRest client;

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return (List<Usuario>) repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> porId(Long id) {
        return repository.findById(id);

    }

    @Override
    @Transactional
    public Usuario guardar(Usuario usuario) {
        return repository.save(usuario);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        repository.deleteById(id);
        client.eliminarCursoUsuarioPorId(id);
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public List<Usuario> ListarPorIds(Iterable<Long> ids) {
        return (List<Usuario>) repository.findAllById(ids);
    }
}
