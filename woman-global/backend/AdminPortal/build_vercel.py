"""
Vercel build: copy static assets to public/ so they are served by CDN.
Flask's static_folder is not used on Vercel; public/ is.
"""
import os
import shutil

_ROOT = os.path.dirname(os.path.abspath(__file__))
STATIC = os.path.join(_ROOT, "static")
PUBLIC = os.path.join(_ROOT, "public")
PUBLIC_STATIC = os.path.join(PUBLIC, "static")

def main():
    os.makedirs(PUBLIC, exist_ok=True)
    if os.path.isdir(STATIC):
        if os.path.exists(PUBLIC_STATIC):
            shutil.rmtree(PUBLIC_STATIC)
        shutil.copytree(STATIC, PUBLIC_STATIC)
        print("Copied static -> public/static")
    # manifest and sw.js are already in public/
    print("Build done.")

if __name__ == "__main__":
    main()
