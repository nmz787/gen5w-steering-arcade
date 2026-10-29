package com.codex.steeringlandscape;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/** A small, depth-tested polygon world. All triangles are uploaded once per GL context. */
final class KartWorld {
    private static final String VERTEX =
        "attribute vec3 pos; attribute vec3 color; attribute vec2 meta;\n" +
        "uniform vec3 ctl; uniform vec3 look; uniform float travel; uniform float flip; uniform float aspect;\n" +
        "varying vec3 vColor; varying float vFog; varying float vType; varying float vTrack;\n" +
        "void main(){\n" +
        " float type=meta.x; vec3 p=pos; float z;\n" +
        " if(type>2.5){\n" +
        "  float cs=cos(flip), sn=sin(flip); p.yz=mat2(cs,sn,-sn,cs)*p.yz;\n" +
        "  p.y+=1.0+abs(sin(flip*.5))*1.1; p.z+=6.0; p.x-=ctl.x*.28; z=p.z;\n" +
        " }else{\n" +
        "  z=type>1.5?mod(meta.y-travel,96.0)+3.0+p.z:p.z; p.z=z;\n" +
        "  p.x-=ctl.x*.0045*z*z;\n" +
        " }\n" +
        " vColor=color; vFog=clamp((z-36.0)/60.0,0.0,1.0); vType=type; vTrack=pos.z+travel;\n" +
        " float y=p.y-3.2; gl_Position=vec4(p.x*1.45/aspect,y*1.45+z*.08,z*.998-.2,z);\n" +
        "}\n";
    private static final String FRAGMENT =
        "precision mediump float; varying vec3 vColor; varying float vFog; varying float vType; varying float vTrack;\n" +
        "uniform vec3 look;\n" +
        "void main(){ vec3 c=vColor;\n" +
        " if(vType<.5){float stripe=step(.5,fract(vTrack*.12));c*=mix(.79,1.0,stripe);}\n" +
        " if(look.x>.5&&look.x<1.5)c=c*vec3(1.15,.82,.72)+vec3(.04,.01,.01);\n" +
        " if(look.x>1.5&&look.x<2.5)c=c*vec3(.72,1.02,1.12);\n" +
        " if(look.x>2.5)c=vec3(dot(c,vec3(.3,.59,.11)));\n" +
        " float k=look.y<.5?.8:(look.y>1.5?1.25:1.0); c=clamp((c-.4)*k+.4,0.0,1.0);\n" +
        " gl_FragColor=vec4(mix(c,vec3(.58,.78,.90),vFog*.72),1.0);}\n";

    private int program, buffer, count;
    private boolean ready;
    private int pos, color, meta, ctl, look, travel, flip, aspect;
    private final ArrayList<Float> mesh = new ArrayList<Float>();

    void create() {
        int vs=compile(GLES20.GL_VERTEX_SHADER,VERTEX), fs=compile(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program); int[] ok=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        String log=GLES20.glGetProgramInfoLog(program); GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
        if(ok[0]==0) throw new IllegalStateException(log);
        pos=GLES20.glGetAttribLocation(program,"pos"); color=GLES20.glGetAttribLocation(program,"color");
        meta=GLES20.glGetAttribLocation(program,"meta"); ctl=GLES20.glGetUniformLocation(program,"ctl");
        look=GLES20.glGetUniformLocation(program,"look"); travel=GLES20.glGetUniformLocation(program,"travel");
        flip=GLES20.glGetUniformLocation(program,"flip"); aspect=GLES20.glGetUniformLocation(program,"aspect");
        mesh.clear(); build(); count=mesh.size()/8;
        FloatBuffer data=ByteBuffer.allocateDirect(mesh.size()*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(Float f:mesh) data.put(f); data.position(0); mesh.clear();
        int[] id=new int[1]; GLES20.glGenBuffers(1,id,0); buffer=id[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,count*8*4,data,GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0); ready=true;
    }
    private int compile(int type,String source) {
        int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s);
        int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(s)); return s;
    }
    void draw(int width,int height,float angle,float gas,float brake,int palette,int contrast,float distance,float rotation) {
        if (!ready) throw new IllegalStateException("Kart shader or mesh failed to initialize");
        GLES20.glViewport(0,0,width,height); GLES20.glClearColor(.58f,.78f,.90f,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glUseProgram(program); GLES20.glUniform3f(ctl,angle,gas,brake);
        GLES20.glUniform3f(look,palette,contrast,0); GLES20.glUniform1f(travel,distance);
        GLES20.glUniform1f(flip,rotation); GLES20.glUniform1f(aspect,(float)width/height);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer);
        GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(color); GLES20.glEnableVertexAttribArray(meta);
        GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,32,0);
        GLES20.glVertexAttribPointer(color,3,GLES20.GL_FLOAT,false,32,12);
        GLES20.glVertexAttribPointer(meta,2,GLES20.GL_FLOAT,false,32,24);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
        GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(color); GLES20.glDisableVertexAttribArray(meta);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0); GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }
    private void v(float x,float y,float z,int rgb,float type,float center) {
        mesh.add(x);mesh.add(y);mesh.add(z);mesh.add(((rgb>>16)&255)/255f);mesh.add(((rgb>>8)&255)/255f);mesh.add((rgb&255)/255f);mesh.add(type);mesh.add(center);
    }
    private void tri(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,int c,float type,float center) {
        v(ax,ay,az,c,type,center);v(bx,by,bz,c,type,center);v(cx,cy,cz,c,type,center);
    }
    private void quad(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,int c,float t,float d) {
        tri(x1,y1,z1,x2,y2,z2,x3,y3,z3,c,t,d);tri(x1,y1,z1,x3,y3,z3,x4,y4,z4,c,t,d);
    }
    private void box(float x,float y,float z,float w,float h,float dep,int c,float t,float center) {
        float a=x-w/2,b=x+w/2,l=z-dep/2,r=z+dep/2;
        quad(a,y,l,b,y,l,b,y+h,l,a,y+h,l,c,t,center);
        quad(b,y,r,a,y,r,a,y+h,r,b,y+h,r,c,t,center);
        quad(a,y,r,a,y,l,a,y+h,l,a,y+h,r,shade(c,.75f),t,center);
        quad(b,y,l,b,y,r,b,y+h,r,b,y+h,l,shade(c,.85f),t,center);
        quad(a,y+h,l,b,y+h,l,b,y+h,r,a,y+h,r,shade(c,1.12f),t,center);
        quad(a,y,r,b,y,r,b,y,l,a,y,l,shade(c,.6f),t,center);
    }
    private int shade(int c,float m){return Math.min(255,(int)(((c>>16)&255)*m))<<16|Math.min(255,(int)(((c>>8)&255)*m))<<8|Math.min(255,(int)((c&255)*m));}
    private void pyramid(float x,float y,float z,float w,float h,int c,float center){
        tri(x-w,y,z-w,x+w,y,z-w,x,y+h,z,c,2,center);
        tri(x+w,y,z-w,x+w,y,z+w,x,y+h,z,shade(c,.8f),2,center);
        tri(x+w,y,z+w,x-w,y,z+w,x,y+h,z,shade(c,.7f),2,center);
        tri(x-w,y,z+w,x-w,y,z-w,x,y+h,z,shade(c,.9f),2,center);
    }
    private void build(){
        // Fixed-depth tessellation bends in the vertex shader; the road pattern scrolls toward the camera.
        for(int i=0;i<48;i++) {float a=1+i*2,b=a+2;
            quad(-6,0,a,6,0,a,6,0,b,-6,0,b,0x8884a1,0,0);
            quad(-6.65f,.02f,a,-6,.02f,a,-6,.02f,b,-6.65f,.02f,b,i%2==0?0xffd45a:0xee526c,1,0);
            quad(6,.02f,a,6.65f,.02f,a,6.65f,.02f,b,6,.02f,b,i%2==0?0xffd45a:0xee526c,1,0);
            quad(-40,-.12f,a,-6.65f,-.12f,a,-6.65f,-.12f,b,-40,-.12f,b,i%2==0?0x61bd51:0x74c960,1,0);
            quad(6.65f,-.12f,a,40,-.12f,a,40,-.12f,b,6.65f,-.12f,b,i%2==0?0x61bd51:0x74c960,1,0);
        }
        // Repeating town of crooked candy houses, pyramidal trees and billboard faces.
        for(int i=0;i<24;i++) {float z=i*4f, x=(i%2==0?-1:1)*(8.5f+(i%3)*1.3f); int flavor=i%3;
            if(flavor==0){
                box(x,0,0,4,3.2f,3,0xffb84f,2,z); pyramid(x,3.2f,0,2.8f,2.4f,0xed5b8c,z);
                box(x,1.1f,-1.53f,1.2f,1.1f,.08f,0x745b90,2,z);
                box(x-1.1f,2f,-1.58f,.65f,.65f,.16f,0xfff9dd,2,z);
                box(x+1.1f,2f,-1.58f,.65f,.65f,.16f,0xfff9dd,2,z);
                box(x-1.1f,2f,-1.68f,.22f,.3f,.1f,0x422b50,2,z);
                box(x+1.1f,2f,-1.68f,.22f,.3f,.1f,0x422b50,2,z);
                box(x,3.3f,-1.5f,1.8f,.25f,.5f,0xffdd4d,2,z);
            } else if(flavor==1){
                box(x,0,0,1,2.4f,1,0xb86951,2,z); pyramid(x,2f,0,2.5f,4.3f,0x1b8f76,z);
                box(x+.8f,3f,-.5f,.45f,.45f,.15f,0xffe55c,2,z);
            } else {
                box(x,0,0,.8f,2,.8f,0x855a8b,2,z);
                box(x,2,0,4,2.8f,.8f,0xf6b486,2,z);
                box(x-.85f,3.5f,-.44f,.65f,.65f,.12f,0xffffff,2,z);
                box(x+.85f,3.5f,-.44f,.65f,.65f,.12f,0xffffff,2,z);
                box(x-.85f,3.5f,-.52f,.22f,.35f,.08f,0x34264d,2,z);
                box(x+.85f,3.5f,-.52f,.22f,.35f,.08f,0x34264d,2,z);
                box(x,2.5f,-.46f,1.5f,.38f,.1f,0x8a304b,2,z);
                box(x+.4f,2.18f,-.53f,.45f,.65f,.12f,0xff6882,2,z);
                box(x-1.5f,4.7f,0,.45f,.8f,.5f,0x3d2857,2,z);
                box(x+1.5f,4.7f,0,.45f,.8f,.5f,0x3d2857,2,z);
            }
        }
        // Clouds and candy mountains are separate faceted objects, wrapping with the town.
        for (int i=0;i<8;i++) { float z=i*12f;
            float side=i%2==0?-1:1;
            pyramid(side*23,0,0,7,10+(i%3)*2, i%2==0?0xb884bf:0xe8889d,z);
            box(side*7,10+(i%3),0,5,1.0f,1.2f,0xfff4d6,2,z);
            box(side*7,11+(i%3),0,2.5f,.8f,1.2f,0xfff4d6,2,z);
        }
        // Kart, driver and wheels are closed 3D meshes, rotating together about X.
        box(0,-.55f,0,2.4f,.48f,2.8f,0xe84b53,3,0);
        box(0,-.08f,.25f,1.65f,.55f,1.3f,0x6b478d,3,0);
        box(0,.45f,.15f,1.0f,.9f,.8f,0xffbd75,3,0);
        box(0,1.30f,.15f,1.1f,.30f,.95f,0x3d526f,3,0);
        box(0,1.53f,.15f,.65f,.18f,.65f,0xffd24d,3,0);
        box(0,.40f,-.28f,.9f,.18f,.1f,0xe76568,3,0);
        box(-.63f,-.1f,-1.1f,.45f,.45f,.45f,0xffc46c,3,0);
        box(.63f,-.1f,-1.1f,.45f,.45f,.45f,0xffc46c,3,0);
        box(-.7f,-.42f,-1.5f,.35f,.35f,.35f,0x596a74,3,0);
        box(.7f,-.42f,-1.5f,.35f,.35f,.35f,0x596a74,3,0);
        for(int x=-1;x<=1;x+=2)for(int z=-1;z<=1;z+=2)
            box(x*1.12f,-.7f,z*.83f,.55f,.65f,.65f,0x242639,3,0);
        box(0,-.3f,-1.42f,1.2f,.18f,.15f,0xffdf62,3,0);
    }
}
