#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <GL/gl.h>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include <vector>
#include <string>
#include <chrono>

template<class F> F load(HMODULE module, const char* name) {
    auto p=GetProcAddress(module,name);
    if (!p) { std::fprintf(stderr,"Missing Mesa export: %s\n",name); ExitProcess(2); }
    return reinterpret_cast<F>(p);
}
int wmain(int argc,wchar_t** argv) {
    if(argc<2||argc>4) return 2;
    bool systemSwap=argc>=3&&std::wcscmp(argv[2],L"--gdi")==0;
    if(argc>=3&&!systemSwap&&std::wcscmp(argv[2],L"--mesa")!=0)return 2;
    bool gameSize=argc==4&&std::wcscmp(argv[3],L"--game-size")==0;
    if(argc==4&&!gameSize)return 2;
    HMODULE mesa=LoadLibraryExW(argv[1],nullptr,LOAD_WITH_ALTERED_SEARCH_PATH);
    if(!mesa) { std::fprintf(stderr,"Mesa load failed: %lu\n",GetLastError());return 3; }
    WNDCLASSW cls{};cls.style=CS_OWNDC;cls.lpfnWndProc=DefWindowProcW;cls.hInstance=GetModuleHandleW(nullptr);cls.lpszClassName=L"MVH isolated Zink probe";
    if(!RegisterClassW(&cls)) return 4;
    RECT rect{0,0,gameSize?1920:256,gameSize?1080:256};
    AdjustWindowRect(&rect,WS_OVERLAPPEDWINDOW,FALSE);
    HWND win=CreateWindowW(cls.lpszClassName,L"MVH Zink hardware validation",WS_OVERLAPPEDWINDOW,0,0,rect.right-rect.left,rect.bottom-rect.top,nullptr,nullptr,cls.hInstance,nullptr);
    HDC dc=GetDC(win);
    auto choose=load<int(WINAPI*)(HDC,const PIXELFORMATDESCRIPTOR*)>(mesa,"wglChoosePixelFormat");
    auto set=load<BOOL(WINAPI*)(HDC,int,const PIXELFORMATDESCRIPTOR*)>(mesa,"wglSetPixelFormat");
    auto create=load<HGLRC(WINAPI*)(HDC)>(mesa,"wglCreateContext");
    auto make=load<BOOL(WINAPI*)(HDC,HGLRC)>(mesa,"wglMakeCurrent");
    auto destroy=load<BOOL(WINAPI*)(HGLRC)>(mesa,"wglDeleteContext");
    auto get=load<PROC(WINAPI*)(LPCSTR)>(mesa,"wglGetProcAddress");
    auto mesaSwap=load<BOOL(WINAPI*)(HDC)>(mesa,"wglSwapBuffers");
    auto swap=systemSwap?&::SwapBuffers:mesaSwap;
    std::printf("SWAP_ROUTE %s CLIENT_SIZE %dx%d\n",systemSwap?"system-GDI32":"Mesa-WGL",gameSize?1920:256,gameSize?1080:256);
    PIXELFORMATDESCRIPTOR pfd{};pfd.nSize=sizeof(pfd);pfd.nVersion=1;pfd.dwFlags=PFD_DRAW_TO_WINDOW|PFD_SUPPORT_OPENGL|PFD_DOUBLEBUFFER;pfd.iPixelType=PFD_TYPE_RGBA;pfd.cColorBits=32;pfd.cAlphaBits=8;pfd.cDepthBits=24;
    int format=choose(dc,&pfd);
    if(!format||!set(dc,format,&pfd)) return 5;
    HGLRC legacy=create(dc);if(!legacy||!make(dc,legacy)) return 6;
    auto createAttribs=reinterpret_cast<HGLRC(WINAPI*)(HDC,HGLRC,const int*)>(get("wglCreateContextAttribsARB"));
    if(!createAttribs) return 7;
    int attributes[]={0x2091,4,0x2092,6,0x9126,1,0};
    HGLRC core=createAttribs(dc,nullptr,attributes);
    if(!core||!make(dc,core)) return 8;
    auto string=load<const GLubyte*(APIENTRY*)(GLenum)>(mesa,"glGetString");
    auto clearColor=load<void(APIENTRY*)(float,float,float,float)>(mesa,"glClearColor");
    auto clear=load<void(APIENTRY*)(GLbitfield)>(mesa,"glClear");
    auto read=load<void(APIENTRY*)(GLint,GLint,GLsizei,GLsizei,GLenum,GLenum,void*)>(mesa,"glReadPixels");
    auto finish=load<void(APIENTRY*)()>(mesa,"glFinish");
    auto error=load<GLenum(APIENTRY*)()>(mesa,"glGetError");
    const char* renderer=reinterpret_cast<const char*>(string(GL_RENDERER));
    std::printf("VENDOR %s\nRENDERER %s\nVERSION %s\n",string(GL_VENDOR),renderer,string(GL_VERSION));
    if(!renderer||!std::strstr(renderer,"zink")||!std::strstr(renderer,"4090")) return 9;
    const char* required[]={"glBufferStorage","glMultiDrawElementsIndirect","glDispatchCompute","glBindBufferBase","glFenceSync","glTexStorage2D","glGetProgramResourceIndex"};
    for(auto name:required) { if(!get(name)) { std::printf("MISSING %s\n",name);return 10; }std::printf("FUNCTION %s PASS\n",name); }
    clearColor(0.25f,0.5f,0.75f,1.f);clear(GL_COLOR_BUFFER_BIT);finish();
    unsigned char rgba[4]{};read(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
    std::printf("READBACK %u %u %u %u ERROR %u\n",rgba[0],rgba[1],rgba[2],rgba[3],error());
    bool pass=rgba[0]>=63&&rgba[0]<=65&&rgba[1]>=127&&rgba[1]<=129&&rgba[2]>=190&&rgba[2]<=192&&rgba[3]==255;
    auto interval=reinterpret_cast<BOOL(WINAPI*)(int)>(get("wglSwapIntervalEXT"));
    auto queryInterval=reinterpret_cast<int(WINAPI*)()>(get("wglGetSwapIntervalEXT"));
    if(!interval||!queryInterval) return 12;
    // Exercise late swapchain changes and the original back-buffer contents.
    for(int value:{0,1,0}) {
        if(!interval(value)||queryInterval()!=value) return 13;
        auto began=std::chrono::steady_clock::now();
        for(int frame=0;frame<48;++frame) {
            clearColor(0.25f,0.5f,0.75f,1.f);clear(GL_COLOR_BUFFER_BIT);
            if(!swap(dc)) {std::printf("SWAP_FAILED %lu\n",GetLastError());return 14;}
        }
        finish();
        auto ms=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-began).count();
        clearColor(0.25f,0.5f,0.75f,1.f);clear(GL_COLOR_BUFFER_BIT);read(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
        GLenum e=error();
        bool valid=rgba[0]>=63&&rgba[0]<=65&&rgba[1]>=127&&rgba[1]<=129&&rgba[2]>=190&&rgba[2]<=192&&rgba[3]==255&&e==0;
        pass=pass&&valid;
        std::printf("PRESENT interval=%d swaps=48 elapsed_ms=%.3f readback=%s error=%u\n",value,ms,valid?"PASS":"FAIL",e);
    }
    make(dc,nullptr);destroy(core);destroy(legacy);ReleaseDC(win,dc);DestroyWindow(win);FreeLibrary(mesa);
    std::puts(pass?"HARDWARE_ZINK_OPENGL46_READBACK_PASS":"READBACK_FAIL");return pass?0:11;
}
