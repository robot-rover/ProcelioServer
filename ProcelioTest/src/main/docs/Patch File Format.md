##Patch Archive Structure
Patches are distributed as .zip archives. They should be named `diff-a.a.a-b.b.b.zip` where the patch transitions the
file tree from version `a.a.a` to version `b.b.b`. They consist of a package manifest, which is always the first zip 
entry in the archive, and then a series of entries which correspond to changed files in the file tree.
 
###Package Manifests 
The package manifest is the first entry in a package archive. It is always a file named `manifests.json`, and is a json
file which holds various information about a patch. It is laid out in this form:
 
    public List<String> filesAndHashes;
    public List<String> ignore;
    public List<String> delete;
    public Integer[] toVersion;
    public Integer[] fromVersion;
    public String newExec;
    
`filesAndHashes` is a list of every file contained in the patch (except for the manifest), and the md5 hash (represented
as a Hex string) for the file after it has been patched. After patching has finished, it is recommended to check the
hash of the file. If it had been modified by the user before patching, there was a network inconsistency, or a
filesystem error, this will catch it. If the hashes don't match, a client can download a fresh copy from the patch
server. 

`ignore` is a list of files that should not be touched by the patcher under any circumstances, usually configuration
files that differ from install to install and should not be patched.

`delete` is a list of files that should be removed from the file tree when patching.

`toVersion` is the version the file tree will be even with once this patch is applied.

`fromVersion` is the version the file tree should be even with when applying this patch. If this is not accurate the
file tree will almost definitely be corrupted.

`newExec` is the file that should be set as the exec in the manifest.json of the file tree after patching.
This is only present if the file tree is executable.

###Package Entries
There are two types of entries present in the package archive after the manifest. File entries and patch entries. Patch
entries are denoted by the `.patch` extension, and every other entry is a file entry. 

####Patch Entries
Zip entries in the package archive with the file extension `.patch` are patch entries. These entries contain the binary
patch data that should be fed into bsdiff Patch along with the original file. The structure of these files is as
follows:

    bytes   description
    1..4    int final_output_file_length
    Block N
    1..4    int block_length (z-4)
    5..z    byte[] block_data
    End Block
        
block N block_data is the patch data that coresponds to block N of the original file.

####File Entries
File entries exist when there is a new file to add to the path or the a patch for the existing file is larger the new
file itself. Therefore, these should be extracted into the file tree, overwriting anything currently at that path.