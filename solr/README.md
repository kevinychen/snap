Solr clue database server
=========================

Install solr:

    https://lucene.apache.org/solr/guide/6_6/installing-solr.html

    or via Homebrew

    $ brew install solr

Start the server:

    $ solr start [-p 8983]

Create a core:

    This will copy the config files and store the data under server/solr/snap
    within your Solr install directory

    $ solr create_core -c snap -d conf

Index the data:

    $ post -c snap clue_data
