/* libpython exports this stable command-line entry point on Android. */
extern int Py_BytesMain(int argc, char **argv);
int main(int argc, char **argv) {
    return Py_BytesMain(argc, argv);
}
