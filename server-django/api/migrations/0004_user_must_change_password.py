from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0003_user_appointment'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='must_change_password',
            field=models.BooleanField(default=False),
        ),
    ]
