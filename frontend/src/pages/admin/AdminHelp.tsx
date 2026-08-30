import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Divider from '@mui/material/Divider';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableRow from '@mui/material/TableRow';

import { SEOHead } from '../../components/common/SEOHead';

/** A link into the back office, so every instruction is one click from the doing. */
function Go({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link component={RouterLink} to={to} sx={{ fontWeight: 600 }}>
      {children}
    </Link>
  );
}

function Section({
  id,
  title,
  lead,
  children,
}: {
  id: string;
  title: string;
  lead: string;
  children: ReactNode;
}) {
  return (
    <Card id={id} sx={{ p: { xs: 2, md: 2.75 }, scrollMarginTop: 80 }}>
      <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem' }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ mb: 1.75 }}>
        {lead}
      </Typography>
      <Divider sx={{ mb: 1.75 }} />
      <Stack spacing={1.25}>{children}</Stack>
    </Card>
  );
}

/** A setting, what it does, and a link to where it is edited. */
function Setting({ name, to, children }: { name: string; to: string; children: ReactNode }) {
  return (
    <TableRow>
      <TableCell sx={{ verticalAlign: 'top', width: 210, pl: 0, borderBottom: 0, pt: 1 }}>
        <Link
          component={RouterLink}
          to={to}
          sx={{ fontFamily: 'monospace', fontSize: 12.5, fontWeight: 600 }}
        >
          {name}
        </Link>
      </TableCell>
      <TableCell sx={{ borderBottom: 0, pt: 1 }}>
        <Typography variant="body2">{children}</Typography>
      </TableCell>
    </TableRow>
  );
}

/**
 * How to run the shop.
 *
 * <p>Written against what the application actually does rather than what it was
 * meant to do. Where a setting exists but nothing reads it yet, this says so -
 * a help page that sends someone off to write a returns policy no customer can
 * reach is worse than no help page.
 */
export function AdminHelp() {
  return (
    <Box sx={{ maxWidth: 900 }}>
      <SEOHead title="Help" path="/admin/help" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem', mb: 0.5 }}>
        Help
      </Typography>
      <Typography variant="body2" sx={{ mb: 3 }}>
        What each screen is for, and the order to do things in. Every setting
        named here links to where it is edited.
      </Typography>

      <Stack spacing={2.5}>
        {/* ---- the order that matters ---- */}
        <Card sx={{ p: { xs: 2, md: 2.75 } }}>
          <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem', mb: 0.5 }}>
            Setting up, in order
          </Typography>
          <Typography variant="body2" sx={{ mb: 1.75 }}>
            The sequence matters: a product needs a category, and a category
            decides which fields that product is asked for.
          </Typography>
          <Divider sx={{ mb: 1.75 }} />

          <Stack spacing={1.25}>
            <Typography variant="body2">
              <strong>1.</strong> <Go to="/admin/settings#store">Name the shop</Go>, set the
              currency and the delivery charge.
            </Typography>
            <Typography variant="body2">
              <strong>2.</strong> <Go to="/admin/settings#mail">Configure email</Go> and send
              yourself a test. Nothing reaches a customer until this works.
            </Typography>
            <Typography variant="body2">
              <strong>3.</strong> <Go to="/admin/categories">Create your categories</Go> — these
              are how the shop is browsed.
            </Typography>
            <Typography variant="body2">
              <strong>4.</strong> <Go to="/admin/attributes">Define the fields</Go> each kind of
              product should have.
            </Typography>
            <Typography variant="body2">
              <strong>5.</strong> <Go to="/admin/vendors">Add your suppliers</Go>, if you buy
              rather than make.
            </Typography>
            <Typography variant="body2">
              <strong>6.</strong> <Go to="/admin/products/new">Add products</Go>, with photographs.
            </Typography>
            <Typography variant="body2">
              <strong>7.</strong> <Go to="/admin/appearance">Choose colours and typefaces</Go>.
            </Typography>
          </Stack>
        </Card>

        {/* ---- selling something new ---- */}
        <Section
          id="new-product-type"
          title="Selling a new kind of thing"
          lead="Books, records, pottery — no developer needed."
        >
          <Typography variant="body2">
            A <Go to="/admin/categories">category</Go> decides two things: how customers browse,
            and what its products are asked for.
          </Typography>
          <Typography variant="body2">
            Give the category a <strong>name for the creator field</strong> — “Author” for books,
            “Artist” for records. Leave it empty for something nobody wrote, and the field
            disappears entirely. Whatever you put there is <em>searchable</em>: a customer
            typing an author's name will find their books.
          </Typography>
          <Typography variant="body2">
            Then <Go to="/admin/attributes">define its attributes</Go> — ISBN, Publisher, Format.
            Attach each to that category, and only its products are asked. An attribute with
            no category applies to everything, which is what a weight or a country of origin
            wants.
          </Typography>
          <Alert severity="info" variant="outlined" sx={{ mt: 0.5 }}>
            Attributes are free text. There is no number or date type yet, and the shop cannot
            filter by them — they appear on the product page and in the admin form.
          </Alert>
        </Section>

        {/* ---- day to day ---- */}
        <Section
          id="orders"
          title="Orders"
          lead="What needs attention, and who to reorder from."
        >
          <Typography variant="body2">
            <Go to="/admin/orders">Orders</Go> lists everything placed. Opening one shows the
            customer, the payment, an internal notes field only you see, and a{' '}
            <strong>Suppliers</strong> panel: who to reorder each line from, their phone and
            address, their terms, and what you paid. That panel is drawn from the supplier set
            on each product, so it is only as useful as{' '}
            <Go to="/admin/vendors">what you record there</Go>.
          </Typography>
          <Typography variant="body2">
            Cancelling an order returns its stock, once. Everything else is a status change and
            a note.
          </Typography>
        </Section>

        <Section
          id="products"
          title="Products and photographs"
          lead="The catalogue, and what a customer sees."
        >
          <Typography variant="body2">
            A product is created as a <strong>draft</strong> and is invisible until you set it
            to <strong>Active</strong>. Leave the slug empty and it is derived from the name;
            after that it stays put, because it is in the URL and a change breaks any link to it.
          </Typography>
          <Typography variant="body2">
            Photographs are added after the product is saved — they attach to it, so it has to
            exist first. <strong>JPEG or PNG</strong>, at least 200px a side, up to 8&nbsp;MB.
            Each upload is resized for you. Describe every photograph in the box provided: that
            text is what someone using a screen reader hears, and what shows if the image fails.
          </Typography>
          <Typography variant="body2">
            The <strong>Copy is still placeholder</strong> switch is a note to yourself. It marks
            the product in your list; customers never see it. It does not hide the placeholder
            text — only writing real copy does that.
          </Typography>
        </Section>

        <Section
          id="enquiries"
          title="Enquiries"
          lead="Messages from the contact form."
        >
          <Typography variant="body2">
            Everything sent through the form is stored here whether or not the email got out, so
            a message is never lost to a mail problem. <Go to="/admin/enquiries">Enquiries</Go>{' '}
            is the record; the notification is a convenience on top of it.
          </Typography>
        </Section>

        <Section
          id="appearance"
          title="Appearance"
          lead="Colours, typefaces and corner rounding."
        >
          <Typography variant="body2">
            <Go to="/admin/appearance">Appearance</Go> previews as you change it. It also reports
            the contrast of each combination: below <strong>4.5:1</strong> body text is hard to
            read in bright light or with weaker eyesight. It will let you save it anyway — it is
            your shop — but it will tell you.
          </Typography>
          <Typography variant="body2">
            Button text colour is chosen for you from whatever accent you pick, because there is
            only one readable answer.
          </Typography>
        </Section>

        {/* ---- settings, by group ---- */}
        <Section
          id="settings"
          title="Settings, group by group"
          lead="Every group on the Settings screen, and what it actually drives."
        >
          <Table size="small">
            <TableBody>
              <Setting name="store.*" to="/admin/settings#store">
                Shop name and tagline, shown in the header, page titles and emails.{' '}
                <strong>store.logo_key does nothing yet</strong> — there is no way to upload a
                logo, and nothing reads it.
              </Setting>
              <Setting name="contact.*" to="/admin/settings#contact">
                <strong>contact.whatsapp_number</strong> drives every WhatsApp link; empty hides
                them all. <strong>contact.public_email</strong> is shown to customers.{' '}
                <strong>contact.admin_email</strong> is where order and enquiry alerts go, and
                overrides the address in the server's environment.{' '}
                <strong>contact.address</strong> appears in the footer, with its line breaks
                kept — write it as you would on an envelope.
              </Setting>
              <Setting name="mail.*" to="/admin/settings#mail">
                SMTP. Fill in host, port, username and password, then use{' '}
                <strong>Send a test email to myself</strong> — delivery failures are hidden from
                shoppers by design, so that button is the only way to know it works. The
                password is never shown again once saved; leaving it blank keeps it.
              </Setting>
              <Setting name="policy.*" to="/admin/settings#policy">
                Delivery, returns and privacy, shown at <em>/shipping</em>, <em>/returns</em> and{' '}
                <em>/privacy</em>. These accept formatting — paragraphs, lists, links — and the
                footer links each one once it has been written. Left empty, the page says it is
                not published and nothing links to it.
              </Setting>
              <Setting name="social.*" to="/admin/settings#social">
                Instagram, Facebook and YouTube. Paste the full address of each —{' '}
                <em>https://instagram.com/yourshop</em>, not a handle — and the icon appears in
                the footer. Leave one empty and it is not shown at all, because an icon linking
                nowhere is worse than no icon.
              </Setting>
              <Setting name="delivery.*" to="/admin/settings#delivery">
                A flat charge on every order, and the subtotal above which it is free. Leave the
                threshold empty to switch that rule off.
              </Setting>
              <Setting name="catalogue.*" to="/admin/settings#catalogue">
                The stock level at which a product is called low on the dashboard.
              </Setting>
              <Setting name="locale.*" to="/admin/settings#locale">
                Currency code and the locale used to format prices and dates.
              </Setting>
              <Setting name="content.*" to="/admin/settings#content">
                <strong>content.checkout_notice</strong> appears at checkout and on the
                confirmation. <strong>story_body</strong>, <strong>why_handmade_body</strong> and{' '}
                <strong>materials_body</strong> are the Our Story page.{' '}
                <strong>content.hero_heading</strong> is the first line on the home page —
                empty falls back to the shop name — and <strong>hero_subheading</strong> is
                the sentence under it, which is also the description search engines show.
              </Setting>
              <Setting name="theme.*" to="/admin/appearance">
                Edited on <strong>Appearance</strong> rather than here, where a preview and the
                contrast readings make the choice a sensible one.
              </Setting>
            </TableBody>
          </Table>
        </Section>

        {/* ---- things not in this screen at all ---- */}
        <Card sx={{ p: { xs: 2, md: 2.75 } }}>
          <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem', mb: 0.5 }}>
            Things you cannot change here
          </Typography>
          <Divider sx={{ my: 1.75 }} />
          <Stack spacing={1.25}>
            <Typography variant="body2">
              <strong>Your own email address.</strong> The account it signs in with is fixed once
              created. Your password is not — use <strong>Change password</strong> in the bar
              above, any time.
            </Typography>
            <Typography variant="body2">
              <strong>The domain, and the database password.</strong> Those live in a file on the
              server and need someone with access to it.
            </Typography>
            <Typography variant="body2">
              <strong>Prices already ordered.</strong> An order keeps what the customer was
              charged; changing a product's price never rewrites what was sold.
            </Typography>
          </Stack>
        </Card>

        <Alert severity="warning" variant="outlined">
          <AlertTitle>Before you take real orders</AlertTitle>
          Send a test email and place a test order yourself, all the way through. Then check the
          order appears under <Go to="/admin/orders">Orders</Go> and that the confirmation
          arrived. Cancel it afterwards to return the stock.
        </Alert>
      </Stack>
    </Box>
  );
}
