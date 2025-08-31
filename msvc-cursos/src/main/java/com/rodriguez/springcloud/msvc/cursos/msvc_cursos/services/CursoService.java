package com.rodriguez.springcloud.msvc.cursos.msvc_cursos.services;

import com.rodriguez.springcloud.msvc.cursos.msvc_cursos.models.Usuario;
import com.rodriguez.springcloud.msvc.cursos.msvc_cursos.models.entity.Curso;

import java.util.List;
import java.util.Optional;

public interface CursoService
{
    List<Curso> listar();
    Optional<Curso> porId(Long id);
    Optional<Curso> porIdConUsuarios(Long id);

    Curso guardar(Curso curso);
    void eliminar(Long cursoId);

    Optional<Usuario> asignarUsuario(Usuario usuario,Long cursoId);
    Optional<Usuario> crearUsuario(Usuario usuario,Long cursoId);
    Optional<Usuario> eliminarUsuario(Usuario usuario,Long cursoId);
    void eliminarCursoUsuarioPorId(Long id);


}
