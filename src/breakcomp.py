with open("brkcmp.txt", "w") as f:
    for i in range(17000):
        f.write(f"u32 x{i} = 0;\n")