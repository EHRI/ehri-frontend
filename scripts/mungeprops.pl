#!/usr/bin/perl

#
# Glorious perl gunk to munge the CSV we get back from
# the translators into Play messages files.
#

use strict;
use warnings;

use Getopt::Long 2.24;
use Config::Properties;
use Text::CSV;
use Hash::Ordered;
use Carp::Assert;

binmode(STDOUT, ":utf8");

# Get command-line options
Getopt::Long::config(qw( permute bundling ));
my $OPT = {};
my $OPT_OK = Getopt::Long::GetOptions($OPT, qw(
    --french|f=s
    --german|d=s
    --polish|p=s
    --append|a
    --help|h
));

my @LANGS = qw(german french polish);

if (!$OPT_OK) {
	print STDERR "Command line parameters were not properly specified.\n\n";
	$OPT->{help} = 1;
}

my $csv = shift(@ARGV);
my $en = shift(@ARGV);

if (not defined $csv or not defined $en) {
    print "Usage: mungeprops <tsv-file> <english-msg>\n";
    exit(1);
}

my $ENGLISH = parse_english($en);
my $DATA = parse_csv($csv);

my $col = 0;
for my $lang (@LANGS) {
    if ($OPT->{$lang}) {
        my $file = $OPT->{$lang};
        write_lang($col, $file);
        $col++;
    }
}


exit(0);

sub num_cols {
    scalar(grep { exists $OPT->{$_} } @LANGS);
}

sub write_lang {
    my $idx = shift;
    my $outfile = shift;
    my $mode = $OPT->{append} ? ">>" : ">";
    open(my $OH, "$mode:encoding(utf8)", $outfile) or die "Unable to open file for writing $outfile!: $!\n";
    if ($OPT->{append}) {
        print $OH "\n";
    }
    foreach my $key ($DATA->keys) {
        my $value = $DATA->get($key)->[$idx];
        print $OH "$key=$value\n";
    }
    close $OH;
}


sub parse_english {
    my $filename = shift;

    my $data = {};

    open(my $F_H, "<:encoding(utf8)", $filename) or die "Unable to open english $filename!: $!\n";
    binmode($F_H, ":utf8");
    my $properties = Config::Properties->new();
    $properties->load($F_H);
    my %props = $properties->properties;
    close($F_H);

    return \%props;
}

sub empty_data {
    my $row = shift;
    my (undef, undef, @langs) = @{$row};
    my $empty = 1;
    for my $v (@langs) {
        if ($v !~ /^\s*$/) {
            $empty = 0;
            last;
        }
    }
    $empty;
}

sub valid_row {
    my $row = shift;
    $row && scalar(@{$row}) == 2 + num_cols();
}

sub parse_csv {
    my $filename = shift;

    open(my $F_H, "<:encoding(utf8)", $filename) or die "Unable to open csv $filename!: $!\n";
    binmode($F_H, ":utf8");

    my $csv = Text::CSV->new ( { binary => 1, sep_char => "\t" } )  # should set binary attribute.
                 or die "Cannot use CSV: ".Text::CSV->error_diag ();

    my $data = Hash::Ordered->new();
    my $prevrow = undef;
    my $currkey = undef;             
    while (my $row = $csv->getline($F_H)) {
        if (valid_row($row) and not empty_data($row)) {
            my ($key, $en, @langs) = @{$row};
            if (defined $key and $key ne "") {
                assert($key !~ /\s/, "Malformed key: $key");
                $data->set($key => \@langs);
                assert(defined $ENGLISH->{$key}, "No English value for key: $key!");
                $currkey = $key;
            } elsif (defined $en and $en !~ /^\s*$/) {
                #    my ($en, $de, $fr, $pl) = @{$row};
                assert(defined $currkey, "Current key is not defined!");
                my @prevs = @{$data->get($currkey)};
                assert(scalar(@prevs) == scalar(@langs),
                    "Different number of continuing line langs to previous!");
                my @next = ();
                for (my $i = 0; $i < scalar(@prevs); $i++) {
                    my $p = $prevs[$i];
                    assert($p =~ /\\$/, "Previous does not end in '\\' for $currkey!: '$p'");                        
                    push @next, $prevs[$i] . "\n  " . $langs[$i];
                }

                $data->set($currkey => \@next);
            }
        }
        $prevrow = $row;
    }        

    close($F_H);
    return $data;
}





