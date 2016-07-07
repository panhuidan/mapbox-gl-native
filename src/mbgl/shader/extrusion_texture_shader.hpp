#pragma once

#include <mbgl/shader/shader.hpp>
#include <mbgl/shader/uniform.hpp>
#include <mbgl/util/color.hpp>

namespace mbgl {

    class ExtrusionTextureShader : public Shader {
    public:
        ExtrusionTextureShader(gl::ObjectStore&);

        void bind(GLbyte *offset) final;

        UniformMatrix<4>                u_matrix   = {"u_matrix", *this};
        Uniform<GLfloat>                u_xdim     = {"u_xdim", *this};
        Uniform<GLfloat>                u_ydim     = {"u_ydim", *this};
        Uniform<GLfloat>                u_opacity  = {"u_opacity", *this};
        Uniform<GLint>                  u_texture  = {"u_texture", *this};
        // TODO is this right or GLbyte? there are no other uniform ints in the shaders
    };
    
} // namespace mbgl
