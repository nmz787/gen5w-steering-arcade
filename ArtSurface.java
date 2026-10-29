package com.codex.steeringlandscape;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GPU-drawn full-screen procedural art, sampled at a modest fixed resolution. */
final class ArtSurface extends GLSurfaceView {
    private final ArtRenderer renderer = new ArtRenderer();
    ArtSurface(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        getHolder().setFixedSize(480, 180);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }
    void setArt(int mode, int palette, int contrast, float angle, float pedal, float brake) {
        renderer.mode = mode; renderer.palette = palette; renderer.contrast = contrast;
        renderer.angle = angle; renderer.pedal = pedal; renderer.brake = brake;
    }
    String error() { return renderer.error; }

    private static final class ArtRenderer implements GLSurfaceView.Renderer {
        volatile int mode, palette, contrast;
        volatile float angle, pedal, brake;
        volatile String error;
        private final int[] programs = new int[3];
        private final KartWorld kart = new KartWorld();
        private float kartDistance, kartFlip;
        private boolean kartFlipping;
        private long kartStatsStart, kartCpuMs;
        private int kartFrames;
        private int position, resolution, time, controls, appearance;
        private int lastProgram = -1;
        private long lastTick;
        private float motionTime, roadTime;
        private int width = 480, height = 180;
        private final FloatBuffer vertices = ByteBuffer.allocateDirect(8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private static final String VERTEX =
                "attribute vec2 pos; void main(){gl_Position=vec4(pos,0.0,1.0);}";
        private static final String FRAGMENT =
                "precision mediump float;\n" +
                "uniform vec2 res; uniform float t; uniform vec3 ctl; uniform vec3 app;\n" +
                "vec3 pal(float x){\n" +
                " vec3 a=vec3(.20,.47,.43), b=vec3(.24,.40,.38), c=vec3(.00,.18,.38);\n" +
                " if(app.y>0.5&&app.y<1.5){a=vec3(.48,.27,.35);b=vec3(.46,.32,.26);c=vec3(.02,.17,.35);}\n" +
                " if(app.y>1.5&&app.y<2.5){a=vec3(.12,.36,.48);b=vec3(.20,.36,.44);c=vec3(.35,.13,.03);}\n" +
                " if(app.y>2.5){a=vec3(.45);b=vec3(.42);c=vec3(.05);}\n" +
                " return a+b*cos(6.28318*(x+c));}\n" +
                "void main(){\n" +
                " vec2 uv=(gl_FragCoord.xy-.5*res)/res.y; float tt=t;\n" +
                " float cs=cos(ctl.x), sn=sin(ctl.x); vec2 p=mat2(cs,-sn,sn,cs)*uv;\n" +
                " vec3 color=vec3(0.0);\n" +
                " if(app.x<4.5){\n" +
                "  float r=length(p); float a=atan(p.y,p.x); a=abs(mod(a+3.14159/8.0,6.28318/8.0)-3.14159/8.0);\n" +
                "  vec2 q=vec2(cos(a),sin(a))*r; float lines=0.0;\n" +
                "  for(int i=0;i<4;i++){float k=float(i); float v=abs(sin(q.x*(12.0+k*3.0)+sin(q.y*10.0-tt+k)+tt)); lines+=.012/(.018+v);}\n" +
                "  float rings=.018/(.025+abs(sin(r*20.0-tt*2.0)));\n" +
                "  color=pal(r*.65+tt*.05)*(.24+lines*.75+rings*.8);\n" +
                " }else if(app.x<5.5){\n" +
                "  float sky=clamp(uv.y+.42,0.0,1.0); color=pal(.55+sky*.15)*(.035+.10*sky);\n" +
                "  for(int i=0;i<4;i++){float k=float(i);float ribbon=.05+k*.105;\n" +
                "   float curve=sin(uv.x*2.5+tt+k*.8)*.08+sin(uv.x*5.0-tt*.5+k)*.025;\n" +
                "   float glow=exp(-abs(uv.y-ribbon-curve)*(10.0+k*2.0));\n" +
                "   float veil=.55+.45*sin(uv.x*18.0+tt*3.0+k*1.7);\n" +
                "   color+=pal(.17+k*.11+tt*.025)*glow*veil*.50;}\n" +
                "  float ridge=abs(uv.y+.34+.04*sin(uv.x*4.0)); color+=pal(.7)*.008/(.01+ridge);\n" +
                " }else if(app.x<6.5){\n" +
                "  vec2 d=p; float x=sin(d.x*11.0+tt)+sin(d.y*13.0-tt*.8);\n" +
                "  x+=sin(length(d-vec2(.35,0.0))*18.0-tt*1.3);\n" +
                "  x+=sin(length(d+vec2(.35,0.0))*18.0+tt);\n" +
                "  float band=.5+.5*cos(x*2.2); color=pal(x*.10+tt*.04)*(.30+.65*band);\n" +
                "  color*=.91+.09*cos(gl_FragCoord.y*3.14159);\n" +
                " }else{\n" +
                "  float a=atan(p.y,p.x); float r=length(p); float petal=abs(sin(a*6.0+tt*.2));\n" +
                "  float pattern=abs(sin(r*22.0-petal*3.0-tt));\n" +
                "  float lead=smoothstep(.04,.14,pattern);\n" +
                "  float cells=floor(r*6.0)+floor((a+3.14159)*3.82);\n" +
                "  color=pal(cells*.07+petal*.2+tt*.025)*(.20+.75*lead);\n" +
                "  color+=pal(.1+petal*.25)*.012/(.025+abs(sin(r*12.0-tt*.4)));\n" +
                " }\n" +
                " float contrast=app.z<.5?.72:(app.z>1.5?1.35:1.0);\n" +
                " color=clamp((color-.25)*contrast+.25,0.0,1.0);\n" +
                " float vignette=1.0-.26*clamp(dot(uv,uv)*.5,0.0,1.0);\n" +
                " gl_FragColor=vec4(color*vignette,1.0);}\n";

        // A second program keeps the existing modes within older mobile shader limits.
        private static final String PLAY_FRAGMENT =
                "precision mediump float;\n" +
                "uniform vec2 res; uniform float t; uniform vec3 ctl; uniform vec3 app;\n" +
                "vec3 pal(float x){\n" +
                " vec3 a=vec3(.46,.35,.32), b=vec3(.46,.35,.36), c=vec3(.05,.28,.52);\n" +
                " if(app.y>.5&&app.y<1.5){a=vec3(.52,.32,.35);b=vec3(.45,.29,.30);c=vec3(.03,.17,.33);}\n" +
                " if(app.y>1.5&&app.y<2.5){a=vec3(.24,.46,.50);b=vec3(.24,.38,.42);c=vec3(.35,.11,.02);}\n" +
                " if(app.y>2.5){a=vec3(.50);b=vec3(.43);c=vec3(.05);}\n" +
                " return a+b*cos(6.28318*(x+c));}\n" +
                "float circle(vec2 p,float r){return 1.0-smoothstep(r-.008,r+.008,length(p));}\n" +
                "void main(){\n" +
                " vec2 uv=(gl_FragCoord.xy-.5*res)/res.y; float a=ctl.x*.65;\n" +
                " vec2 p=mat2(cos(a),-sin(a),sin(a),cos(a))*uv; float brake=ctl.z;\n" +
                " vec3 col;\n" +
                " if(app.x<8.5){\n" +
                "  vec2 f=p; f.y=(f.y+.02)*(1.0+.8*brake); f.x*=1.0-.16*brake;\n" +
                "  float wobble=.015*sin(f.y*28.0+t*3.0)*sin(f.x*18.0-t);\n" +
                "  float face=circle(f+vec2(wobble,0.0),.34);\n" +
                "  float rays=.5+.5*sin(atan(p.y,p.x)*15.0+t*.5);\n" +
                "  col=pal(.52+.08*rays)*(.18+.17*rays);\n" +
                "  vec3 skin=pal(.08+.08*sin(f.y*12.0+t));\n" +
                "  float grain=.92+.08*sin(f.x*140.0)*sin(f.y*120.0); col=mix(col,skin*grain,face);\n" +
                "  float e1=circle((f-vec2(-.12,.07))*vec2(1.0,1.15),.073+.025*ctl.y);\n" +
                "  float e2=circle((f-vec2(.12,.07))*vec2(1.0,1.15),.073+.025*ctl.y);\n" +
                "  col=mix(col,vec3(.96,.93,.78),max(e1,e2));\n" +
                "  float pupils=max(circle(f-vec2(-.10,.06),.027),circle(f-vec2(.14,.06),.027));\n" +
                "  col=mix(col,vec3(.08,.05,.10),pupils);\n" +
                "  vec2 mouth=f-vec2(0.0,-.14); mouth.y+=.7*mouth.x*mouth.x;\n" +
                "  float grin=(1.0-smoothstep(.012,.022,abs(mouth.y)))*(1.0-smoothstep(.18,.19,abs(mouth.x)));\n" +
                "  col=mix(col,vec3(.16,.05,.12),grin);\n" +
                "  float cheek=max(circle(f-vec2(-.22,-.08),.04),circle(f-vec2(.22,-.08),.04));\n" +
                "  col=mix(col,pal(.26),cheek*.65*face);\n" +
                " }else if(app.x<9.5){\n" +
                "  float field=0.0; float tint=0.0;\n" +
                "  for(int i=0;i<6;i++){float k=float(i);float ang=k*1.0472+t*.35;\n" +
                "   vec2 center=vec2(cos(ang),sin(ang))*(.26+.11*sin(t+k))*(1.0-.55*brake);\n" +
                "   float d=length(p-center); float blob=.012/(.012+d*d); field+=blob; tint+=blob*k*.12;}\n" +
                "  float edge=smoothstep(.70,.76,field); float outline=smoothstep(.58,.68,field)-edge;\n" +
                "  col=mix(pal(.62)*.12,pal(tint*.14+t*.04),edge); col=mix(col,pal(.03),outline*.9);\n" +
                "  col+=pal(.25)*.025/(.025+abs(field-.38));\n" +
                " }else{\n" +
                "  float x=sin(p.x*9.0+sin(p.y*6.0+t)*1.7+t)+cos(p.y*12.0+sin(p.x*5.0-t));\n" +
                "  x+=.55*sin(length(p)*24.0-t*1.6+ctl.y*3.0);\n" +
                "  float ink=abs(sin(x*4.0+brake*3.0));\n" +
                "  float paper=.88+.12*sin(p.x*150.0)*sin(p.y*130.0);\n" +
                "  float line=1.0-smoothstep(.04,.15+brake*.08,ink);\n" +
                "  col=mix(pal(.15+x*.08)*paper,pal(.64)*.12,line);\n" +
                " }\n" +
                " float contrast=app.z<.5?.72:(app.z>1.5?1.30:1.0);\n" +
                " gl_FragColor=vec4(clamp((col-.3)*contrast+.3,0.0,1.0),1.0); }\n";

        // Pseudo-3D road: one shader pass, no geometry or per-frame texture uploads.
        private static final String ROAD_FRAGMENT =
                "precision mediump float;\n" +
                "uniform vec2 res; uniform float t; uniform vec3 ctl; uniform vec3 app;\n" +
                "void main(){\n" +
                " vec2 uv=gl_FragCoord.xy/res; float x=uv.x*2.0-1.0; float y=uv.y;\n" +
                " vec3 sky1=vec3(.08,.10,.26), sky2=vec3(.96,.48,.36);\n" +
                " vec3 land1=vec3(.16,.33,.30), land2=vec3(.22,.43,.30);\n" +
                " if(app.y>0.5&&app.y<1.5){sky1=vec3(.19,.10,.30);sky2=vec3(1.0,.52,.29);land1=vec3(.35,.18,.26);land2=vec3(.49,.26,.27);}\n" +
                " if(app.y>1.5&&app.y<2.5){sky1=vec3(.03,.12,.25);sky2=vec3(.44,.72,.76);land1=vec3(.08,.28,.34);land2=vec3(.12,.40,.38);}\n" +
                " if(app.y>2.5){sky1=vec3(.10);sky2=vec3(.68);land1=vec3(.23);land2=vec3(.34);}\n" +
                " float horizon=.48; float sky=clamp((y-horizon)/.52,0.0,1.0);\n" +
                " vec3 col=mix(sky2,sky1,sky);\n" +
                " vec2 sun=vec2((uv.x-.73)*res.x/res.y, y-.72);\n" +
                " float disc=1.0-smoothstep(.09,.095,length(sun));\n" +
                " col=mix(col,vec3(1.0,.79,.50),disc*.85);\n" +
                " float hills=.49+.055*abs(sin(x*3.0+ctl.x*.4))+.045*abs(sin(x*7.0+1.2));\n" +
                " if(y<hills && y>=horizon) col=mix(land1,sky2,.24);\n" +
                " if(y<horizon){\n" +
                "  float q=clamp((horizon-y)/horizon,0.0,1.0);\n" +
                "  float depth=2.7/(q+.10); float bands=step(.5,fract(depth*.32+t));\n" +
                "  col=mix(land1,land2,bands);\n" +
                "  float center=-ctl.x*.53*(1.0-q)*(1.0-q);\n" +
                "  float halfWidth=.018+1.08*pow(q,1.35); float d=abs(x-center);\n" +
                "  float curb=.012+.05*q; float road=1.0-step(halfWidth,d);\n" +
                "  float shoulder=step(halfWidth,d)*(1.0-step(halfWidth+curb,d));\n" +
                "  vec3 pavement=mix(vec3(.21,.23,.29),vec3(.25,.27,.32),bands*.35);\n" +
                "  col=mix(col,pavement,road);\n" +
                "  col=mix(col,mix(vec3(.92,.84,.71),vec3(.85,.16,.16),bands),shoulder);\n" +
                "  float divider=1.0-step(.004+.01*q,d);\n" +
                "  float dash=step(.44,fract(depth*.36+t*1.3));\n" +
                "  col=mix(col,vec3(.98,.91,.68),divider*dash*road);\n" +
                "  float haze=1.0-smoothstep(.0,.16,q); col=mix(col,sky2,haze*.28);\n" +
                " }\n" +
                " float k=app.z<.5?.8:(app.z>1.5?1.2:1.0); col=clamp((col-.35)*k+.35,0.0,1.0);\n" +
                " gl_FragColor=vec4(col,1.0); }\n";

        ArtRenderer() { vertices.put(new float[]{-1,-1, 1,-1, -1,1, 1,1}).position(0); }
        @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            for (int i = 0; i < 3; i++) {
                try {
                    int vs = shader(GLES20.GL_VERTEX_SHADER, VERTEX);
                    int fs = shader(GLES20.GL_FRAGMENT_SHADER, i == 0 ? FRAGMENT : i == 1 ? PLAY_FRAGMENT : ROAD_FRAGMENT);
                    int program = GLES20.glCreateProgram();
                    GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs);
                    GLES20.glLinkProgram(program);
                    int[] status = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
                    String info = GLES20.glGetProgramInfoLog(program);
                    GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
                    if (status[0] == 0) throw new IllegalStateException(info);
                    programs[i] = program;
                } catch (RuntimeException e) {
                    programs[i] = 0;
                    Log.e("SteeringLandscape", "Shader initialization failed for program " + i, e);
                }
            }
            try { kart.create(); } catch (RuntimeException e) { Log.e("SteeringLandscape", "Kart renderer initialization failed", e); }
            lastProgram = -1;
        }
        private int shader(int type, String code) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, code); GLES20.glCompileShader(shader);
            int[] status = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String info = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader);
                throw new IllegalStateException(info);
            }
            return shader;
        }
        @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
            width = Math.max(1, w); height = Math.max(1, h); GLES20.glViewport(0, 0, width, height);
        }
        @Override public void onDrawFrame(GL10 unused) {
            if (mode == 12) {
                long now = SystemClock.uptimeMillis();
                float dt = lastTick == 0 ? 0 : Math.min(.1f, (now-lastTick)/1000f); lastTick=now;
                float gas = Math.max(0f, Math.min(1f, pedal/100f));
                float braking = Math.max(0f, Math.min(1f, brake));
                kartDistance = (kartDistance + dt*(3f+24f*gas)*(1f-.45f*braking)) % 96f;
                if(braking>.03f){kartFlipping=true; kartFlip += dt*(1.92f+11.34f*braking);}
                else if(kartFlipping){kartFlip+=dt*4.89f;}
                if(kartFlip>=6.2831853f){kartFlip%=6.2831853f;if(braking<=.03f){kartFlip=0;kartFlipping=false;}}
                long drawStart=SystemClock.uptimeMillis();
                try { kart.draw(width,height,Math.max(-1f,Math.min(1f,angle/450f))*1.2f,gas,braking,palette,contrast,kartDistance,kartFlip); error=null; }
                catch(RuntimeException e){error="Kart renderer unavailable"; Log.e("SteeringLandscape",error,e);}
                kartCpuMs += SystemClock.uptimeMillis()-drawStart; kartFrames++;
                if (kartStatsStart==0) kartStatsStart=now;
                if (now-kartStatsStart>=10000) {
                    Log.i("SteeringLandscape", "Kart GL submissions: " + kartFrames + " in " + (now-kartStatsStart)
                            + " ms; mean CPU submit " + ((float)kartCpuMs/kartFrames) + " ms");
                    kartStatsStart=now; kartFrames=0; kartCpuMs=0;
                }
                lastProgram=-1; return;
            }
            GLES20.glClearColor(.02f, .03f, .07f, 1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            int program = programs[mode >= 11 ? 2 : mode >= 8 ? 1 : 0];
            if (program == 0) { error = "OpenGL unavailable for this mode"; return; }
            error = null;
            GLES20.glUseProgram(program);
            if (lastProgram != program) {
                position = GLES20.glGetAttribLocation(program, "pos");
                resolution = GLES20.glGetUniformLocation(program, "res");
                time = GLES20.glGetUniformLocation(program, "t");
                controls = GLES20.glGetUniformLocation(program, "ctl");
                appearance = GLES20.glGetUniformLocation(program, "app");
                lastProgram = program;
            }
            long now = SystemClock.uptimeMillis();
            float dt = lastTick == 0 ? 0 : Math.min(.1f, (now - lastTick) / 1000f);
            lastTick = now;
            float gas = Math.max(0f, Math.min(1f, pedal / 100f));
            float braking = Math.max(0f, Math.min(1f, brake));
            motionTime = (motionTime + dt * (.13f + .20f * gas) * (1f - .70f * braking)) % 1000f;
            roadTime = (roadTime + dt * (.18f + 2.2f * gas) * (1f - .45f * braking)) % 1000f;
            GLES20.glUniform2f(resolution, width, height);
            GLES20.glUniform1f(time, mode >= 11 ? roadTime : motionTime);
            GLES20.glUniform3f(controls, Math.max(-1f, Math.min(1f, angle / 450f)) * 1.2f,
                    gas, braking);
            GLES20.glUniform3f(appearance, mode, palette, contrast);
            GLES20.glEnableVertexAttribArray(position);
            vertices.position(0);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
        }

    }
}
